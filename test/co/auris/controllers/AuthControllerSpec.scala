// ─── test/co/auris/controllers/AuthControllerSpec.scala ───────────────────────
//
// Full-stack integration tests for AuthController: real routing, real JSON
// (de)serialization, real JwtAuthAction, real AuthService, real Postgres
// (auris_test schema — see application.test.conf and PlayIntegrationSpec).

package co.auris.controllers

import co.auris.services.AuthService
import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.FakeRequest
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration._

class AuthControllerSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def signUpBody(email: String, password: String = "password123", role: String = "patient"): JsValue =
    Json.obj("email" -> email, "password" -> password, "role" -> role)

  /** Signs up a fresh patient via the real endpoint and returns the parsed response body. */
  private def signUpPatient(email: String): JsValue = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up").withJsonBody(signUpBody(email))).get
    status(res) mustBe CREATED
    contentAsJson(res)
  }

  "POST /api/auth/sign-up" should {
    "create a patient account and issue tokens" in {
      val body = signUpPatient("charlotte@example.com")

      (body \ "user" \ "email").as[String] mustBe "charlotte@example.com"
      (body \ "user" \ "role").as[String] mustBe "patient"
      (body \ "user" \ "patient").isDefined mustBe true
      (body \ "user" \ "surgeon").toOption mustBe None
      (body \ "accessToken").as[String] must not be empty
      (body \ "refreshToken").as[String] must not be empty
      (body \ "tokenType").as[String] mustBe "Bearer"
    }

    "create a surgeon account with a surgeon profile, not a patient profile" in {
      val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
        .withJsonBody(signUpBody("james@example.com", role = "surgeon"))).get

      status(res) mustBe CREATED
      val body = contentAsJson(res)
      (body \ "user" \ "role").as[String] mustBe "surgeon"
      (body \ "user" \ "surgeon").isDefined mustBe true
      (body \ "user" \ "patient").toOption mustBe None
    }

    "reject a duplicate email with 409" in {
      signUpPatient("duplicate@example.com")

      val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
        .withJsonBody(signUpBody("duplicate@example.com"))).get

      status(res) mustBe CONFLICT
      (contentAsJson(res) \ "code").as[String] mustBe "EMAIL_EXISTS"
    }

    "reject a password under 8 characters" in {
      val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
        .withJsonBody(signUpBody("shortpw@example.com", password = "short"))).get

      status(res) mustBe BAD_REQUEST
      (contentAsJson(res) \ "code").as[String] mustBe "WEAK_PASSWORD"
    }

    "reject a malformed request body" in {
      val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
        .withJsonBody(Json.obj("email" -> "no-password-or-role@example.com"))).get

      status(res) mustBe BAD_REQUEST
      (contentAsJson(res) \ "code").as[String] mustBe "VALIDATION_ERROR"
    }
  }

  "POST /api/auth/sign-in" should {
    "succeed with the correct credentials" in {
      signUpPatient("signin@example.com")

      val res = route(app, FakeRequest(POST, "/api/auth/sign-in")
        .withJsonBody(Json.obj("email" -> "signin@example.com", "password" -> "password123"))).get

      status(res) mustBe OK
      (contentAsJson(res) \ "user" \ "email").as[String] mustBe "signin@example.com"
    }

    "reject the wrong password with 401" in {
      signUpPatient("wrongpw@example.com")

      val res = route(app, FakeRequest(POST, "/api/auth/sign-in")
        .withJsonBody(Json.obj("email" -> "wrongpw@example.com", "password" -> "not-the-password"))).get

      status(res) mustBe UNAUTHORIZED
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_CREDENTIALS"
    }

    "reject an unknown email with the same code as a wrong password" in {
      val res = route(app, FakeRequest(POST, "/api/auth/sign-in")
        .withJsonBody(Json.obj("email" -> "ghost@example.com", "password" -> "whatever123"))).get

      status(res) mustBe UNAUTHORIZED
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_CREDENTIALS"
    }

    "reject a deactivated account with 403" in {
      signUpPatient("inactive@example.com")
      Await.result(db.run(sqlu"UPDATE users SET is_active = false WHERE email = 'inactive@example.com'"), 10.seconds)

      val res = route(app, FakeRequest(POST, "/api/auth/sign-in")
        .withJsonBody(Json.obj("email" -> "inactive@example.com", "password" -> "password123"))).get

      status(res) mustBe FORBIDDEN
      (contentAsJson(res) \ "code").as[String] mustBe "ACCOUNT_INACTIVE"
    }
  }

  "POST /api/auth/refresh" should {
    "rotate the token and reject reuse of the old one" in {
      val signUpRes = signUpPatient("refresh@example.com")
      val oldRefreshToken = (signUpRes \ "refreshToken").as[String]

      val res = route(app, FakeRequest(POST, "/api/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> oldRefreshToken))).get

      status(res) mustBe OK
      val newRefreshToken = (contentAsJson(res) \ "refreshToken").as[String]
      newRefreshToken must not be oldRefreshToken

      // The old (now-revoked) token must no longer work.
      val reuseRes = route(app, FakeRequest(POST, "/api/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> oldRefreshToken))).get
      status(reuseRes) mustBe UNAUTHORIZED
      (contentAsJson(reuseRes) \ "code").as[String] mustBe "INVALID_TOKEN"
    }

    "reject an unrecognised token with 401" in {
      val res = route(app, FakeRequest(POST, "/api/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> "not-a-real-token"))).get

      status(res) mustBe UNAUTHORIZED
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_TOKEN"
    }
  }

  "POST /api/auth/sign-out" should {
    "revoke the refresh token so it can no longer be used" in {
      val signUpRes = signUpPatient("signout@example.com")
      val refreshToken = (signUpRes \ "refreshToken").as[String]

      val res = route(app, FakeRequest(POST, "/api/auth/sign-out")
        .withJsonBody(Json.obj("refreshToken" -> refreshToken))).get
      status(res) mustBe NO_CONTENT

      val afterSignOut = route(app, FakeRequest(POST, "/api/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> refreshToken))).get
      status(afterSignOut) mustBe UNAUTHORIZED
    }
  }

  "POST /api/auth/sign-out-all" should {
    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(POST, "/api/auth/sign-out-all")).get

      status(res) mustBe UNAUTHORIZED
    }

    "revoke every session for the user" in {
      val signUpRes = signUpPatient("signoutall@example.com")
      val accessToken = (signUpRes \ "accessToken").as[String]
      val refreshToken = (signUpRes \ "refreshToken").as[String]

      val res = route(app, FakeRequest(POST, "/api/auth/sign-out-all")
        .withHeaders("Authorization" -> s"Bearer $accessToken")).get
      status(res) mustBe NO_CONTENT

      val afterSignOutAll = route(app, FakeRequest(POST, "/api/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> refreshToken))).get
      status(afterSignOutAll) mustBe UNAUTHORIZED
    }
  }

  "GET /api/auth/me" should {
    "return the authenticated user's profile" in {
      val signUpRes = signUpPatient("me@example.com")
      val accessToken = (signUpRes \ "accessToken").as[String]

      val res = route(app, FakeRequest(GET, "/api/auth/me")
        .withHeaders("Authorization" -> s"Bearer $accessToken")).get

      status(res) mustBe OK
      (contentAsJson(res) \ "email").as[String] mustBe "me@example.com"
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(GET, "/api/auth/me")).get

      status(res) mustBe UNAUTHORIZED
    }

    "reject a garbage bearer token" in {
      val res = route(app, FakeRequest(GET, "/api/auth/me")
        .withHeaders("Authorization" -> "Bearer not-a-real-jwt")).get

      status(res) mustBe UNAUTHORIZED
    }
  }

  "POST /api/auth/verify-email" should {
    "verify a valid token and mark the user's email verified" in {
      signUpPatient("verify@example.com")
      val token = Await.result(
        db.run(sql"SELECT token FROM email_verifications WHERE user_id = (SELECT id FROM users WHERE email = 'verify@example.com')".as[String]),
        10.seconds
      ).head

      val res = route(app, FakeRequest(POST, "/api/auth/verify-email").withJsonBody(Json.obj("token" -> token))).get

      status(res) mustBe OK

      val isVerified = Await.result(
        db.run(sql"SELECT is_email_verified FROM users WHERE email = 'verify@example.com'".as[Boolean]),
        10.seconds
      ).head
      isVerified mustBe true
    }

    "reject reusing an already-consumed token" in {
      signUpPatient("reuse-verify@example.com")
      val token = Await.result(
        db.run(sql"SELECT token FROM email_verifications WHERE user_id = (SELECT id FROM users WHERE email = 'reuse-verify@example.com')".as[String]),
        10.seconds
      ).head

      val first = route(app, FakeRequest(POST, "/api/auth/verify-email").withJsonBody(Json.obj("token" -> token))).get
      status(first) mustBe OK

      val second = route(app, FakeRequest(POST, "/api/auth/verify-email").withJsonBody(Json.obj("token" -> token))).get
      status(second) mustBe BAD_REQUEST
      (contentAsJson(second) \ "code").as[String] mustBe "INVALID_TOKEN"
    }

    "reject an unknown token" in {
      val res = route(app, FakeRequest(POST, "/api/auth/verify-email").withJsonBody(Json.obj("token" -> "no-such-token"))).get

      status(res) mustBe BAD_REQUEST
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_TOKEN"
    }
  }

  "POST /api/auth/forgot-password" should {
    "return a generic success message for a known email" in {
      signUpPatient("forgot@example.com")

      val res = route(app, FakeRequest(POST, "/api/auth/forgot-password").withJsonBody(Json.obj("email" -> "forgot@example.com"))).get

      status(res) mustBe OK
    }

    "return the same generic message for an unknown email, to avoid enumeration" in {
      val res = route(app, FakeRequest(POST, "/api/auth/forgot-password").withJsonBody(Json.obj("email" -> "no-such-account@example.com"))).get

      status(res) mustBe OK
    }
  }

  "POST /api/auth/reset-password" should {
    // forgotPassword deliberately never returns the raw reset token over HTTP
    // (only its SHA-256 hash is persisted) — a real user gets it by email.
    // AuthService is called in-process here purely to obtain that token,
    // exactly as clicking the emailed link would; everything downstream
    // still goes through the real HTTP endpoint.
    "reset the password, invalidate old sessions, and allow signing in with the new password" in {
      val signUpRes = signUpPatient("reset@example.com")
      val oldRefreshToken = (signUpRes \ "refreshToken").as[String]

      val authService = app.injector.instanceOf[AuthService]
      val rawToken = Await.result(authService.initiatePasswordReset("reset@example.com"), 10.seconds).get

      val res = route(app, FakeRequest(POST, "/api/auth/reset-password")
        .withJsonBody(Json.obj("token" -> rawToken, "newPassword" -> "newPassword456"))).get
      status(res) mustBe OK

      // Old sessions are revoked.
      val refreshAfterReset = route(app, FakeRequest(POST, "/api/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> oldRefreshToken))).get
      status(refreshAfterReset) mustBe UNAUTHORIZED

      // Old password no longer works.
      val oldPasswordSignIn = route(app, FakeRequest(POST, "/api/auth/sign-in")
        .withJsonBody(Json.obj("email" -> "reset@example.com", "password" -> "password123"))).get
      status(oldPasswordSignIn) mustBe UNAUTHORIZED

      // New password works.
      val newPasswordSignIn = route(app, FakeRequest(POST, "/api/auth/sign-in")
        .withJsonBody(Json.obj("email" -> "reset@example.com", "password" -> "newPassword456"))).get
      status(newPasswordSignIn) mustBe OK
    }

    "reject an invalid reset token" in {
      val res = route(app, FakeRequest(POST, "/api/auth/reset-password")
        .withJsonBody(Json.obj("token" -> "not-a-real-token", "newPassword" -> "newPassword456"))).get

      status(res) mustBe BAD_REQUEST
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_TOKEN"
    }

    "reject a new password under 8 characters" in {
      signUpPatient("weakreset@example.com")
      val authService = app.injector.instanceOf[AuthService]
      val rawToken = Await.result(authService.initiatePasswordReset("weakreset@example.com"), 10.seconds).get

      val res = route(app, FakeRequest(POST, "/api/auth/reset-password")
        .withJsonBody(Json.obj("token" -> rawToken, "newPassword" -> "short"))).get

      status(res) mustBe BAD_REQUEST
      (contentAsJson(res) \ "code").as[String] mustBe "WEAK_PASSWORD"
    }
  }
}
