// ─── test/co/auris/controllers/AuthRateLimitSpec.scala ────────────────────────
//
// Rate limiting is disabled globally in application.test.conf (every
// FakeRequest shares the "127.0.0.1" remote address, so a real limit would
// trip across unrelated specs sharing a GuiceOneAppPerSuite app). This spec
// turns it back on for its own app instance, with tiny thresholds, to prove
// the enforcement path actually works end-to-end.

package co.auris.controllers

import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

class AuthRateLimitSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "auris.rateLimit.enabled"                 -> true,
        "auris.rateLimit.forgotPassword.maxAttempts"   -> 3,
        "auris.rateLimit.forgotPassword.windowMinutes" -> 15
      )
      .build()

  "POST /api/auth/forgot-password" should {
    "allow requests up to the configured limit, then reject with 429" in {
      val body = Json.obj("email" -> "rate-limited@example.com")

      (1 to 3).foreach { _ =>
        val res = route(app, FakeRequest(POST, "/api/auth/forgot-password").withJsonBody(body)).get
        status(res) mustBe OK
      }

      val blocked = route(app, FakeRequest(POST, "/api/auth/forgot-password").withJsonBody(body)).get
      status(blocked) mustBe TOO_MANY_REQUESTS
      (contentAsJson(blocked) \ "code").as[String] mustBe "RATE_LIMITED"
    }
  }
}
