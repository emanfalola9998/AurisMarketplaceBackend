// ─── app/co/auris/services/BillingService.scala ───────────────────────────────
//
// Surgeon billing: an annual membership fee (a plain Stripe subscription —
// surgeons pay Auris directly, there's no Stripe Connect account splitting
// payouts) plus a 1% platform fee recorded against every paid booking.
// Auris pays surgeons their share manually outside Stripe; platform_fees is
// the ledger that payout gets reconciled against.

package co.auris.services

import co.auris.models.{PlatformFeeType, SubscriptionStatus}
import co.auris.repositories.{PlatformFeeRepository, SurgeonRepository, UserRepository}
import play.api.Configuration

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait BillingError
object BillingError {
  case object SurgeonNotFound   extends BillingError
  case object AlreadySubscribed extends BillingError
}

final case class MembershipSubscription(clientSecret: String)

final case class SurgeonFeesSummary(
                                     surgeonId:   UUID,
                                     surgeonName: String,
                                     totalOwed:   BigDecimal,
                                     fees:        List[co.auris.models.PlatformFee]
                                   )
object SurgeonFeesSummary {
  import play.api.libs.json._
  implicit val writes: OWrites[SurgeonFeesSummary] = Json.writes[SurgeonFeesSummary]
}

@Singleton
class BillingService @Inject() (
                                 surgeonRepository:     SurgeonRepository,
                                 platformFeeRepository: PlatformFeeRepository,
                                 userRepository:        UserRepository,
                                 paymentService:        PaymentService,
                                 config:                Configuration
                               )(implicit ec: ExecutionContext) {

  private val annualFee: BigDecimal = BigDecimal(config.get[Int]("auris.billing.annualMembershipFeeGbp"))
  val TransactionFeeRate: BigDecimal = BigDecimal(config.get[Double]("auris.billing.transactionFeeRate"))

  /** Starts (or resumes, if a customer already exists) the annual membership
   *  subscription for a surgeon and returns the client secret to confirm the
   *  first payment with — same embedded-payment pattern as booking deposits.
   */
  def subscribeToMembership(surgeonUserId: UUID): Future[Either[BillingError, MembershipSubscription]] =
    surgeonRepository.findByUserId(surgeonUserId).flatMap {
      case None => Future.successful(Left(BillingError.SurgeonNotFound))
      case Some(s) if s.subscriptionStatus == SubscriptionStatus.Active =>
        Future.successful(Left(BillingError.AlreadySubscribed))
      case Some(s) =>
        for {
          userOpt    <- userRepository.findById(surgeonUserId)
          customerId <- s.stripeCustomerId match {
                          case Some(id) => Future.successful(id)
                          case None =>
                            paymentService.createCustomer(userOpt.map(_.email).getOrElse(""), s.id).flatMap { id =>
                              surgeonRepository.setStripeCustomerId(s.id, id).map(_ => id)
                            }
                        }
          subResult  <- paymentService.createAnnualMembershipSubscription(customerId, s.id, annualFee)
        } yield Right(MembershipSubscription(subResult.clientSecret))
    }

  /** Called from the Stripe webhook once invoice.paid fires for a membership
   *  subscription: marks the surgeon active for another year and records the
   *  fee in the ledger.
   */
  def confirmMembershipPayment(customerId: String): Future[Unit] =
    surgeonRepository.findByStripeCustomerId(customerId).flatMap {
      case None => Future.successful(())
      case Some(s) =>
        val renewsAt = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1)
        for {
          _ <- surgeonRepository.setSubscriptionStatus(s.id, SubscriptionStatus.Active, Some(renewsAt))
          _ <- platformFeeRepository.create(s.id, None, PlatformFeeType.AnnualMembership, annualFee)
        } yield ()
    }

  /** Records Auris's 1% cut of a paid booking. Idempotent — safe to call
   *  again for the same booking (e.g. on a webhook redelivery).
   */
  def recordTransactionFee(surgeonId: UUID, bookingId: UUID, bookingFee: BigDecimal): Future[Unit] =
    platformFeeRepository.existsForBooking(bookingId, PlatformFeeType.Transaction).flatMap {
      case true => Future.successful(())
      case false =>
        val fee = (bookingFee * TransactionFeeRate).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        platformFeeRepository.create(surgeonId, Some(bookingId), PlatformFeeType.Transaction, fee).map(_ => ())
    }

  /** Admin view: every surgeon with unpaid fees, grouped, with a running total
   *  — what Auris still owes each surgeon for their next manual payout.
   */
  def listUnpaidFeesBySurgeon(): Future[List[SurgeonFeesSummary]] =
    platformFeeRepository.listUnpaid().flatMap { fees =>
      val bySurgeon = fees.groupBy(_.surgeonId).toList
      Future.traverse(bySurgeon) { case (surgeonId, surgeonFees) =>
        surgeonRepository.findById(surgeonId).map { surgeonOpt =>
          SurgeonFeesSummary(
            surgeonId   = surgeonId,
            surgeonName = surgeonOpt.map(s => s"${s.title} ${s.firstName} ${s.lastName}".trim).getOrElse("Unknown surgeon"),
            totalOwed   = surgeonFees.map(_.amount).sum,
            fees        = surgeonFees
          )
        }
      }
    }

  def markFeePaidOut(id: UUID): Future[Int] = platformFeeRepository.markPaidOut(id)
}
