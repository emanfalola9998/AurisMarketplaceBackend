// ─── test/co/auris/controllers/WriteEndpointRateLimitSpec.scala ──────────────
//
// Rate limiting is disabled globally in application.test.conf (every
// FakeRequest shares the "127.0.0.1" remote address, so a real limit would
// trip across unrelated specs sharing a GuiceOneAppPerSuite app — see
// AuthRateLimitSpec). This spec turns it back on for its own app instance,
// with tiny thresholds, to prove enquiry/booking/message/review creation are
// actually rate-limited — public-facing write endpoints that previously had
// no limit at all despite auth's sign-in/sign-up/password endpoints having
// one since the beginning.

package co.auris.controllers

import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.util.UUID

class WriteEndpointRateLimitSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "auris.rateLimit.enabled"                     -> true,
        // High enough that this spec's own repeated sign-ups (setup for each
        // test below) never trip the unrelated signUp bucket itself.
        "auris.rateLimit.signUp.maxAttempts"           -> 100,
        "auris.rateLimit.enquiryCreate.maxAttempts"   -> 2,
        "auris.rateLimit.enquiryCreate.windowMinutes" -> 15,
        "auris.rateLimit.bookingCreate.maxAttempts"   -> 2,
        "auris.rateLimit.bookingCreate.windowMinutes" -> 15,
        "auris.rateLimit.messageSend.maxAttempts"     -> 2,
        "auris.rateLimit.messageSend.windowMinutes"   -> 15,
        "auris.rateLimit.reviewCreate.maxAttempts"    -> 2,
        "auris.rateLimit.reviewCreate.windowMinutes"  -> 15
      )
      .build()

  private def signUp(email: String, role: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def userId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/auth/me")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def surgeonProfileId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  "POST /api/enquiries" should {
    "allow requests up to the configured limit, then reject with 429" in {
      val patientToken = signUp("ratelimit-enquiry-patient@example.com", "patient")
      val surgeonToken = signUp("ratelimit-enquiry-surgeon@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val body = Json.obj("surgeonId" -> surgeonId.toString, "procedureInterest" -> "Rhinoplasty")

      (1 to 2).foreach { _ =>
        val res = route(app, FakeRequest(POST, "/api/enquiries")
          .withHeaders("Authorization" -> s"Bearer $patientToken")
          .withJsonBody(body)).get
        status(res) mustBe CREATED
      }

      val blocked = route(app, FakeRequest(POST, "/api/enquiries")
        .withHeaders("Authorization" -> s"Bearer $patientToken")
        .withJsonBody(body)).get
      status(blocked) mustBe TOO_MANY_REQUESTS
      (contentAsJson(blocked) \ "code").as[String] mustBe "RATE_LIMITED"
    }
  }

  "POST /api/bookings" should {
    "allow requests up to the configured limit, then reject with 429" in {
      val patientToken = signUp("ratelimit-booking-patient@example.com", "patient")
      // An invalid body is fine here — the rate limiter runs before body
      // validation, so a 400 still counts as a request against the bucket.
      val badBody = Json.obj()

      (1 to 2).foreach { _ =>
        val res = route(app, FakeRequest(POST, "/api/bookings")
          .withHeaders("Authorization" -> s"Bearer $patientToken")
          .withJsonBody(badBody)).get
        status(res) must not be TOO_MANY_REQUESTS
      }

      val blocked = route(app, FakeRequest(POST, "/api/bookings")
        .withHeaders("Authorization" -> s"Bearer $patientToken")
        .withJsonBody(badBody)).get
      status(blocked) mustBe TOO_MANY_REQUESTS
      (contentAsJson(blocked) \ "code").as[String] mustBe "RATE_LIMITED"
    }
  }

  "POST /api/messages" should {
    "allow requests up to the configured limit, then reject with 429" in {
      val senderToken = signUp("ratelimit-msg-sender@example.com", "patient")
      val recipientToken = signUp("ratelimit-msg-recipient@example.com", "surgeon")
      val recipientId = userId(recipientToken)
      val body = Json.obj("recipientId" -> recipientId.toString, "body" -> "Hello")

      (1 to 2).foreach { _ =>
        val res = route(app, FakeRequest(POST, "/api/messages")
          .withHeaders("Authorization" -> s"Bearer $senderToken")
          .withJsonBody(body)).get
        status(res) mustBe CREATED
      }

      val blocked = route(app, FakeRequest(POST, "/api/messages")
        .withHeaders("Authorization" -> s"Bearer $senderToken")
        .withJsonBody(body)).get
      status(blocked) mustBe TOO_MANY_REQUESTS
      (contentAsJson(blocked) \ "code").as[String] mustBe "RATE_LIMITED"
    }
  }

  "POST /api/reviews" should {
    "allow requests up to the configured limit, then reject with 429" in {
      val patientToken = signUp("ratelimit-review-patient@example.com", "patient")
      // An invalid body is fine here too — same reasoning as bookings above.
      val badBody = Json.obj()

      (1 to 2).foreach { _ =>
        val res = route(app, FakeRequest(POST, "/api/reviews")
          .withHeaders("Authorization" -> s"Bearer $patientToken")
          .withJsonBody(badBody)).get
        status(res) must not be TOO_MANY_REQUESTS
      }

      val blocked = route(app, FakeRequest(POST, "/api/reviews")
        .withHeaders("Authorization" -> s"Bearer $patientToken")
        .withJsonBody(badBody)).get
      status(blocked) mustBe TOO_MANY_REQUESTS
      (contentAsJson(blocked) \ "code").as[String] mustBe "RATE_LIMITED"
    }
  }
}
