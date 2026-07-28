// ─── app/co/auris/repositories/PlatformFeeRepository.scala ────────────────────
//
// Ledger of what Auris has earned from each surgeon: a 1% cut of every paid
// booking, plus each surgeon's annual membership fee. Auris collects all
// patient payments directly (no Stripe Connect) and pays surgeons their share
// manually outside Stripe — this ledger is what that payout is reconciled
// against, and paidOut/paidOutAt just record that reconciliation happened.

package co.auris.repositories

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.PlatformFees
import co.auris.models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatformFeeRepository @Inject() (
                                        protected val dbConfigProvider: DatabaseConfigProvider
                                      )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  def create(surgeonId: UUID, bookingId: Option[UUID], feeType: PlatformFeeType, amount: BigDecimal): Future[PlatformFee] = {
    val fee = PlatformFee(
      id        = UUID.randomUUID(),
      surgeonId = surgeonId,
      bookingId = bookingId,
      feeType   = feeType,
      amount    = amount,
      createdAt = OffsetDateTime.now(ZoneOffset.UTC)
    )
    db.run((PlatformFees += fee).map(_ => fee))
  }

  /** True if a fee of this type already exists for this booking — guards the
   *  1% transaction fee against being recorded twice on a webhook redelivery.
   */
  def existsForBooking(bookingId: UUID, feeType: PlatformFeeType): Future[Boolean] =
    db.run(PlatformFees.filter(f => f.bookingId === bookingId && f.feeType === feeType).exists.result)

  def listForSurgeon(surgeonId: UUID): Future[List[PlatformFee]] =
    db.run(PlatformFees.filter(_.surgeonId === surgeonId).sortBy(_.createdAt.desc).result).map(_.toList)

  def listUnpaid(): Future[List[PlatformFee]] =
    db.run(PlatformFees.filter(_.paidOut === false).sortBy(_.createdAt.asc).result).map(_.toList)

  def markPaidOut(id: UUID): Future[Int] =
    db.run(
      PlatformFees
        .filter(_.id === id)
        .map(f => (f.paidOut, f.paidOutAt))
        .update((true, Some(OffsetDateTime.now(ZoneOffset.UTC))))
    )
}
