// ─── test/co/auris/services/BillingServiceSpec.scala ──────────────────────────

package co.auris.services

import co.auris.models._
import co.auris.repositories.{PlatformFeeRepository, SurgeonRepository, UserRepository}
import co.auris.support.Fixtures
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BillingServiceSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with ArgumentMatchersSugar
  with BeforeAndAfterEach {

  private var surgeonRepository:     SurgeonRepository     = _
  private var platformFeeRepository: PlatformFeeRepository = _
  private var userRepository:        UserRepository        = _
  private var paymentService:        PaymentService        = _
  private var service: BillingService = _

  private val testConfig = Configuration(
    "auris.billing.annualMembershipFeeGbp" -> 2400,
    "auris.billing.transactionFeeRate"     -> 0.01
  )

  override def beforeEach(): Unit = {
    surgeonRepository     = mock[SurgeonRepository]
    platformFeeRepository = mock[PlatformFeeRepository]
    userRepository        = mock[UserRepository]
    paymentService        = mock[PaymentService]
    service = new BillingService(surgeonRepository, platformFeeRepository, userRepository, paymentService, testConfig)
    ()
  }

  "subscribeToMembership" should {
    "fail with SurgeonNotFound when the caller has no surgeon profile" in {
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(None))

      val result = service.subscribeToMembership(UUID.randomUUID()).futureValue

      result mustBe Left(BillingError.SurgeonNotFound)
    }

    "fail with AlreadySubscribed when the surgeon's membership is already active" in {
      val surgeon = Fixtures.surgeonProfile(subscriptionStatus = SubscriptionStatus.Active)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))

      val result = service.subscribeToMembership(surgeon.userId).futureValue

      result mustBe Left(BillingError.AlreadySubscribed)
    }

    "creates a new Stripe customer when the surgeon doesn't have one yet" in {
      val surgeon = Fixtures.surgeonProfile(stripeCustomerId = None)
      val user = Fixtures.user(email = "james@example.com")
      when(surgeonRepository.findByUserId(surgeon.userId)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(surgeon.userId)).thenReturn(Future.successful(Some(user)))
      when(paymentService.createCustomer(eqTo("james@example.com"), eqTo(surgeon.id)))
        .thenReturn(Future.successful("cus_new123"))
      when(surgeonRepository.setStripeCustomerId(surgeon.id, "cus_new123")).thenReturn(Future.successful(1))
      when(paymentService.createAnnualMembershipSubscription(eqTo("cus_new123"), eqTo(surgeon.id), eqTo(BigDecimal(2400))))
        .thenReturn(Future.successful(SubscriptionResult("sub_123", "sub_123_secret")))

      val result = service.subscribeToMembership(surgeon.userId).futureValue

      result mustBe Right(MembershipSubscription("sub_123_secret"))
      verify(paymentService).createCustomer(eqTo("james@example.com"), eqTo(surgeon.id))
      verify(surgeonRepository).setStripeCustomerId(surgeon.id, "cus_new123")
    }

    "reuses the existing Stripe customer instead of creating a new one" in {
      val surgeon = Fixtures.surgeonProfile(stripeCustomerId = Some("cus_existing"))
      when(surgeonRepository.findByUserId(surgeon.userId)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(surgeon.userId)).thenReturn(Future.successful(Some(Fixtures.user())))
      when(paymentService.createAnnualMembershipSubscription(eqTo("cus_existing"), eqTo(surgeon.id), any[BigDecimal]))
        .thenReturn(Future.successful(SubscriptionResult("sub_123", "sub_123_secret")))

      val result = service.subscribeToMembership(surgeon.userId).futureValue

      result mustBe Right(MembershipSubscription("sub_123_secret"))
      verify(paymentService, never).createCustomer(any[String], any[UUID])
    }
  }

  "confirmMembershipPayment" should {
    "activate the surgeon's subscription and record the annual fee" in {
      val surgeon = Fixtures.surgeonProfile(stripeCustomerId = Some("cus_123"))
      when(surgeonRepository.findByStripeCustomerId("cus_123")).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.setSubscriptionStatus(eqTo(surgeon.id), eqTo(SubscriptionStatus.Active), any[Option[OffsetDateTime]]))
        .thenReturn(Future.successful(1))
      when(platformFeeRepository.create(surgeon.id, None, PlatformFeeType.AnnualMembership, BigDecimal(2400)))
        .thenReturn(Future.successful(Fixtures.platformFee(surgeonId = surgeon.id)))

      service.confirmMembershipPayment("cus_123").futureValue

      verify(surgeonRepository).setSubscriptionStatus(eqTo(surgeon.id), eqTo(SubscriptionStatus.Active), any[Option[OffsetDateTime]])
      verify(platformFeeRepository).create(surgeon.id, None, PlatformFeeType.AnnualMembership, BigDecimal(2400))
    }

    "does nothing when no surgeon matches the Stripe customer id" in {
      when(surgeonRepository.findByStripeCustomerId("cus_unknown")).thenReturn(Future.successful(None))

      service.confirmMembershipPayment("cus_unknown").futureValue

      verify(surgeonRepository, never).setSubscriptionStatus(any[UUID], any[SubscriptionStatus], any[Option[OffsetDateTime]])
    }
  }

  "markSubscriptionPastDue" should {
    "flag the surgeon as past due, preserving their current renewal date" in {
      val renewsAt = OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2)
      val surgeon = Fixtures.surgeonProfile(stripeCustomerId = Some("cus_123"))
        .copy(subscriptionRenewsAt = Some(renewsAt))
      when(surgeonRepository.findByStripeCustomerId("cus_123")).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.setSubscriptionStatus(surgeon.id, SubscriptionStatus.PastDue, Some(renewsAt)))
        .thenReturn(Future.successful(1))

      service.markSubscriptionPastDue("cus_123").futureValue

      verify(surgeonRepository).setSubscriptionStatus(surgeon.id, SubscriptionStatus.PastDue, Some(renewsAt))
    }

    "does nothing when no surgeon matches the Stripe customer id" in {
      when(surgeonRepository.findByStripeCustomerId("cus_unknown")).thenReturn(Future.successful(None))

      service.markSubscriptionPastDue("cus_unknown").futureValue

      verify(surgeonRepository, never).setSubscriptionStatus(any[UUID], any[SubscriptionStatus], any[Option[OffsetDateTime]])
    }
  }

  "markSubscriptionCanceled" should {
    "cancel the surgeon's subscription and clear their renewal date" in {
      val surgeon = Fixtures.surgeonProfile(stripeCustomerId = Some("cus_123"))
      when(surgeonRepository.findByStripeCustomerId("cus_123")).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.setSubscriptionStatus(surgeon.id, SubscriptionStatus.Canceled, None))
        .thenReturn(Future.successful(1))

      service.markSubscriptionCanceled("cus_123").futureValue

      verify(surgeonRepository).setSubscriptionStatus(surgeon.id, SubscriptionStatus.Canceled, None)
    }

    "does nothing when no surgeon matches the Stripe customer id" in {
      when(surgeonRepository.findByStripeCustomerId("cus_unknown")).thenReturn(Future.successful(None))

      service.markSubscriptionCanceled("cus_unknown").futureValue

      verify(surgeonRepository, never).setSubscriptionStatus(any[UUID], any[SubscriptionStatus], any[Option[OffsetDateTime]])
    }
  }

  "syncSubscriptionStatus" should {
    "map Stripe's active status to Active, preserving the renewal date" in {
      val renewsAt = OffsetDateTime.now(ZoneOffset.UTC).plusMonths(3)
      val surgeon = Fixtures.surgeonProfile(stripeCustomerId = Some("cus_123")).copy(subscriptionRenewsAt = Some(renewsAt))
      when(surgeonRepository.findByStripeCustomerId("cus_123")).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.setSubscriptionStatus(surgeon.id, SubscriptionStatus.Active, Some(renewsAt)))
        .thenReturn(Future.successful(1))

      service.syncSubscriptionStatus("cus_123", "active").futureValue

      verify(surgeonRepository).setSubscriptionStatus(surgeon.id, SubscriptionStatus.Active, Some(renewsAt))
    }

    List("past_due", "unpaid", "incomplete").foreach { stripeStatus =>
      s"map Stripe's $stripeStatus status to PastDue" in {
        val surgeon = Fixtures.surgeonProfile(stripeCustomerId = Some("cus_123"))
        when(surgeonRepository.findByStripeCustomerId("cus_123")).thenReturn(Future.successful(Some(surgeon)))
        when(surgeonRepository.setSubscriptionStatus(surgeon.id, SubscriptionStatus.PastDue, surgeon.subscriptionRenewsAt))
          .thenReturn(Future.successful(1))

        service.syncSubscriptionStatus("cus_123", stripeStatus).futureValue

        verify(surgeonRepository).setSubscriptionStatus(surgeon.id, SubscriptionStatus.PastDue, surgeon.subscriptionRenewsAt)
      }
    }

    List("canceled", "incomplete_expired").foreach { stripeStatus =>
      s"map Stripe's $stripeStatus status to Canceled, clearing the renewal date" in {
        val surgeon = Fixtures.surgeonProfile(stripeCustomerId = Some("cus_123"))
        when(surgeonRepository.findByStripeCustomerId("cus_123")).thenReturn(Future.successful(Some(surgeon)))
        when(surgeonRepository.setSubscriptionStatus(surgeon.id, SubscriptionStatus.Canceled, None))
          .thenReturn(Future.successful(1))

        service.syncSubscriptionStatus("cus_123", stripeStatus).futureValue

        verify(surgeonRepository).setSubscriptionStatus(surgeon.id, SubscriptionStatus.Canceled, None)
      }
    }

    "ignores unrecognized statuses rather than guessing" in {
      val surgeon = Fixtures.surgeonProfile(stripeCustomerId = Some("cus_123"))
      when(surgeonRepository.findByStripeCustomerId("cus_123")).thenReturn(Future.successful(Some(surgeon)))

      service.syncSubscriptionStatus("cus_123", "trialing").futureValue

      verify(surgeonRepository, never).setSubscriptionStatus(any[UUID], any[SubscriptionStatus], any[Option[OffsetDateTime]])
    }

    "does nothing when no surgeon matches the Stripe customer id" in {
      when(surgeonRepository.findByStripeCustomerId("cus_unknown")).thenReturn(Future.successful(None))

      service.syncSubscriptionStatus("cus_unknown", "active").futureValue

      verify(surgeonRepository, never).setSubscriptionStatus(any[UUID], any[SubscriptionStatus], any[Option[OffsetDateTime]])
    }
  }

  "recordTransactionFee" should {
    "records 1% of the booking fee" in {
      val surgeonId = UUID.randomUUID()
      val bookingId = UUID.randomUUID()
      when(platformFeeRepository.existsForBooking(bookingId, PlatformFeeType.Transaction)).thenReturn(Future.successful(false))
      when(platformFeeRepository.create(surgeonId, Some(bookingId), PlatformFeeType.Transaction, BigDecimal("1.05")))
        .thenReturn(Future.successful(Fixtures.platformFee(surgeonId = surgeonId)))

      service.recordTransactionFee(surgeonId, bookingId, BigDecimal(105)).futureValue

      verify(platformFeeRepository).create(surgeonId, Some(bookingId), PlatformFeeType.Transaction, BigDecimal("1.05"))
    }

    "does not record a duplicate fee for the same booking" in {
      val surgeonId = UUID.randomUUID()
      val bookingId = UUID.randomUUID()
      when(platformFeeRepository.existsForBooking(bookingId, PlatformFeeType.Transaction)).thenReturn(Future.successful(true))

      service.recordTransactionFee(surgeonId, bookingId, BigDecimal(105)).futureValue

      verify(platformFeeRepository, never).create(any[UUID], any[Option[UUID]], any[PlatformFeeType], any[BigDecimal])
    }
  }

  "listUnpaidFeesBySurgeon" should {
    "groups unpaid fees by surgeon with a running total" in {
      val surgeon = Fixtures.surgeonProfile()
      val fee1 = Fixtures.platformFee(surgeonId = surgeon.id, amount = BigDecimal("1.05"))
      val fee2 = Fixtures.platformFee(surgeonId = surgeon.id, amount = BigDecimal("2.10"))
      when(platformFeeRepository.listUnpaid()).thenReturn(Future.successful(List(fee1, fee2)))
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))

      val result = service.listUnpaidFeesBySurgeon().futureValue

      result must have size 1
      result.head.surgeonId mustBe surgeon.id
      result.head.totalOwed mustBe BigDecimal("3.15")
      result.head.fees must contain theSameElementsAs List(fee1, fee2)
    }
  }
}
