// ─── test/co/auris/controllers/BillingControllerSpec.scala ──────────────────
//
// Full-stack integration tests for surgeon membership subscription and the
// admin platform-fee ledger. PaymentService is overridden with a mock so
// these never make a real Stripe call — the placeholder stripe.secretKey in
// application.conf/test.conf isn't a usable key anyway.

package co.auris.controllers

import co.auris.models.{PlatformFeeType, SubscriptionStatus, UserRole}
import co.auris.repositories.{PlatformFeeRepository, SurgeonRepository, UserRepository}
import co.auris.services.{PaymentService, SubscriptionResult}
import co.auris.support.PlayIntegrationSpec
import org.mindrot.jbcrypt.BCrypt
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class BillingControllerSpec extends AnyWordSpec
  with Matchers
  with PlayIntegrationSpec
  with MockitoSugar
  with ArgumentMatchersSugar {

  private val paymentService: PaymentService = mock[PaymentService]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[PaymentService].toInstance(paymentService))
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(paymentService)
    ()
  }

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

  private def signUp(email: String, role: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def signUpAdminAndSignIn(email: String, password: String = "password123"): String = {
    val userRepository = app.injector.instanceOf[UserRepository]
    val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(4))
    await(userRepository.create(email, passwordHash, UserRole.Admin))

    val res = route(app, FakeRequest(POST, "/api/auth/sign-in")
      .withJsonBody(Json.obj("email" -> email, "password" -> password))).get
    status(res) mustBe OK
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def surgeonProfileId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  "POST /api/surgeons/membership/subscribe" should {
    "create a Stripe customer and subscription, returning its client secret" in {
      when(paymentService.createCustomer(any[String], any[UUID])).thenReturn(Future.successful("cus_test123"))
      when(paymentService.createAnnualMembershipSubscription(any[String], any[UUID], any[BigDecimal]))
        .thenReturn(Future.successful(SubscriptionResult("sub_test123", "sub_test123_secret")))

      val surgeonToken = signUp("billing-surgeon1@example.com", "surgeon")

      val res = route(app, FakeRequest(POST, "/api/surgeons/membership/subscribe")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get

      status(res) mustBe OK
      (contentAsJson(res) \ "stripeClientSecret").as[String] mustBe "sub_test123_secret"
      verify(paymentService).createCustomer(any[String], any[UUID])
    }

    "reject a surgeon who's already subscribed" in {
      val surgeonToken = signUp("billing-surgeon2@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val surgeonRepository = app.injector.instanceOf[SurgeonRepository]
      await(surgeonRepository.setSubscriptionStatus(surgeonId, SubscriptionStatus.Active, None))

      val res = route(app, FakeRequest(POST, "/api/surgeons/membership/subscribe")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get

      status(res) mustBe CONFLICT
    }

    "reject a non-surgeon" in {
      val patientToken = signUp("billing-patient1@example.com", "patient")

      val res = route(app, FakeRequest(POST, "/api/surgeons/membership/subscribe")
        .withHeaders("Authorization" -> s"Bearer $patientToken")).get

      status(res) mustBe FORBIDDEN
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(POST, "/api/surgeons/membership/subscribe")).get

      status(res) mustBe UNAUTHORIZED
    }
  }

  "GET /api/admin/platform-fees" should {
    "list unpaid fees grouped by surgeon with a running total" in {
      val adminToken = signUpAdminAndSignIn("billing-admin1@example.com")
      val surgeonToken = signUp("billing-surgeon3@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)

      val platformFeeRepository = app.injector.instanceOf[PlatformFeeRepository]
      await(platformFeeRepository.create(surgeonId, None, PlatformFeeType.Transaction, BigDecimal("1.05")))
      await(platformFeeRepository.create(surgeonId, None, PlatformFeeType.Transaction, BigDecimal("2.10")))

      val res = route(app, FakeRequest(GET, "/api/admin/platform-fees")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe OK
      val items = (contentAsJson(res) \ "items").as[List[play.api.libs.json.JsObject]]
      items must have size 1
      (items.head \ "surgeonId").as[String] mustBe surgeonId.toString
      (items.head \ "totalOwed").as[BigDecimal] mustBe BigDecimal("3.15")
    }

    "reject a non-admin" in {
      val surgeonToken = signUp("billing-surgeon4@example.com", "surgeon")

      val res = route(app, FakeRequest(GET, "/api/admin/platform-fees")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get

      status(res) mustBe FORBIDDEN
    }
  }

  "PUT /api/admin/platform-fees/:id/mark-paid" should {
    "mark a fee as paid out so it no longer appears in the unpaid list" in {
      val adminToken = signUpAdminAndSignIn("billing-admin2@example.com")
      val surgeonToken = signUp("billing-surgeon5@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)

      val platformFeeRepository = app.injector.instanceOf[PlatformFeeRepository]
      val fee = await(platformFeeRepository.create(surgeonId, None, PlatformFeeType.Transaction, BigDecimal("1.05")))

      val res = route(app, FakeRequest(PUT, s"/api/admin/platform-fees/${fee.id}/mark-paid")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe OK

      val listRes = route(app, FakeRequest(GET, "/api/admin/platform-fees")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get
      (contentAsJson(listRes) \ "items").as[List[play.api.libs.json.JsObject]] mustBe empty
    }

    "reject a non-admin" in {
      val adminOnlyToken = signUp("billing-surgeon6@example.com", "surgeon")

      val res = route(app, FakeRequest(PUT, s"/api/admin/platform-fees/${UUID.randomUUID()}/mark-paid")
        .withHeaders("Authorization" -> s"Bearer $adminOnlyToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe FORBIDDEN
    }
  }
}
