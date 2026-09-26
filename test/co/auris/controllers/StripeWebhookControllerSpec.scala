// ─── test/co/auris/controllers/StripeWebhookControllerSpec.scala ────────────
//
// Full-stack integration tests for the Stripe webhook endpoint. Payloads are
// signed for real, using the app's own configured stripe.webhookSecret (read
// from its Configuration at runtime, whatever that resolves to) — this
// never talks to Stripe, it only proves PaymentService.verifyWebhookSignature
// + the event-type routing work end to end against genuinely valid
// signatures, the same way Stripe's own signing scheme works.

package co.auris.controllers

import co.auris.models.{ConsultationType, SubscriptionStatus}
import co.auris.repositories.{BookingRepository, SurgeonRepository}
import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class StripeWebhookControllerSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def webhookSecret: String = app.configuration.get[String]("stripe.webhookSecret")

  private def stripeSignatureHeader(payload: String, timestamp: Long = System.currentTimeMillis() / 1000): String = {
    val signedPayload = s"$timestamp.$payload"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(webhookSecret.getBytes("UTF-8"), "HmacSHA256"))
    val signature = mac.doFinal(signedPayload.getBytes("UTF-8")).map("%02x".format(_)).mkString
    s"t=$timestamp,v1=$signature"
  }

  private def postWebhook(payload: String, signatureHeader: Option[String]) = {
    val req = FakeRequest(POST, "/api/stripe/webhook")
      .withHeaders(signatureHeader.toList.map("Stripe-Signature" -> _): _*)
      .withTextBody(payload)
    route(app, req).get
  }

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

  private def signUp(email: String, role: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def surgeonProfileId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def patientProfileId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/patients/dashboard")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def paymentIntentSucceededPayload(bookingId: UUID): String =
    Json.obj(
      "id"          -> "evt_test_pi",
      "object"      -> "event",
      "api_version" -> "2024-06-20",
      "created"     -> (System.currentTimeMillis() / 1000),
      "type"        -> "payment_intent.succeeded",
      "data" -> Json.obj(
        "object" -> Json.obj(
          "id"       -> "pi_test123",
          "object"   -> "payment_intent",
          "amount"   -> 10000,
          "currency" -> "gbp",
          "status"   -> "succeeded",
          "metadata" -> Json.obj("bookingId" -> bookingId.toString)
        )
      )
    ).toString

  private def invoicePaidPayload(customerId: String): String =
    Json.obj(
      "id"          -> "evt_test_inv",
      "object"      -> "event",
      "api_version" -> "2024-06-20",
      "created"     -> (System.currentTimeMillis() / 1000),
      "type"        -> "invoice.paid",
      "data" -> Json.obj(
        "object" -> Json.obj(
          "id"       -> "in_test123",
          "object"   -> "invoice",
          "customer" -> customerId,
          "status"   -> "paid"
        )
      )
    ).toString

  private def unrecognizedEventPayload: String =
    Json.obj(
      "id"          -> "evt_test_other",
      "object"      -> "event",
      "api_version" -> "2024-06-20",
      "created"     -> (System.currentTimeMillis() / 1000),
      "type"        -> "customer.created",
      "data"        -> Json.obj("object" -> Json.obj("id" -> "cus_test", "object" -> "customer"))
    ).toString

  "POST /api/stripe/webhook" should {
    "reject a payload with no Stripe-Signature header" in {
      val res = postWebhook(unrecognizedEventPayload, None)

      status(res) mustBe UNAUTHORIZED
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_SIGNATURE"
    }

    "reject a payload with an invalid signature" in {
      val res = postWebhook(unrecognizedEventPayload, Some("t=1,v1=not-a-real-signature"))

      status(res) mustBe UNAUTHORIZED
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_SIGNATURE"
    }

    "acknowledge with 200 an event type it doesn't act on" in {
      val payload = unrecognizedEventPayload
      val res = postWebhook(payload, Some(stripeSignatureHeader(payload)))

      status(res) mustBe OK
    }

    "confirm a booking's payment on payment_intent.succeeded" in {
      val surgeonToken = signUp("webhook-surgeon1@example.com", "surgeon")
      val patientToken = signUp("webhook-patient1@example.com", "patient")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientId = patientProfileId(patientToken)

      val bookingRepository = app.injector.instanceOf[BookingRepository]
      val booking = await(bookingRepository.createBooking(
        enquiryId        = None,
        patientId        = patientId,
        surgeonId        = surgeonId,
        consultationType = ConsultationType.Virtual,
        scheduledAt       = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
        durationMinutes  = 60,
        fee              = BigDecimal(200)
      ))

      val payload = paymentIntentSucceededPayload(booking.id)
      val res = postWebhook(payload, Some(stripeSignatureHeader(payload)))

      status(res) mustBe OK
      val updated = await(bookingRepository.findBookingById(booking.id)).get
      updated.status.toString mustBe "Confirmed"
    }

    "acknowledge with 200 when the payment_intent's bookingId doesn't match any booking" in {
      val payload = paymentIntentSucceededPayload(UUID.randomUUID())
      val res = postWebhook(payload, Some(stripeSignatureHeader(payload)))

      status(res) mustBe OK
    }

    "activate a surgeon's membership on invoice.paid" in {
      val surgeonToken = signUp("webhook-surgeon2@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val surgeonRepository = app.injector.instanceOf[SurgeonRepository]
      await(surgeonRepository.setStripeCustomerId(surgeonId, "cus_test_webhook2"))

      val payload = invoicePaidPayload("cus_test_webhook2")
      val res = postWebhook(payload, Some(stripeSignatureHeader(payload)))

      status(res) mustBe OK
      val updated = await(surgeonRepository.findById(surgeonId)).get
      updated.subscriptionStatus mustBe SubscriptionStatus.Active
    }

    "acknowledge with 200 when invoice.paid's customer matches no surgeon" in {
      val payload = invoicePaidPayload("cus_unknown_customer")
      val res = postWebhook(payload, Some(stripeSignatureHeader(payload)))

      status(res) mustBe OK
    }
  }
}
