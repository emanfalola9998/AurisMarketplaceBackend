// ─── test/co/auris/controllers/DeleteAccountSpec.scala ───────────────────────
//
// Full-stack integration tests for DELETE /api/patients/account.

package co.auris.controllers

import co.auris.models.ConsultationType
import co.auris.repositories.{BookingRepository, PatientRepository, UserRepository}
import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class DeleteAccountSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def signUp(email: String, role: String, password: String = "password123"): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> password, "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def ownId(accessToken: String, path: String): UUID = {
    val res = route(app, FakeRequest(GET, path).withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

  private def deleteAccount(token: String, password: String) =
    route(app, FakeRequest(DELETE, "/api/patients/account")
      .withHeaders("Authorization" -> s"Bearer $token")
      .withJsonBody(Json.obj("password" -> password))).get

  "DELETE /api/patients/account" should {
    "deactivate the account, anonymize the profile, and block future sign-in" in {
      val token = signUp("delete-patient1@example.com", "patient", "correctPass123")
      val patientId = ownId(token, "/api/patients/dashboard")

      val res = deleteAccount(token, "correctPass123")
      status(res) mustBe OK

      val patientRepository = app.injector.instanceOf[PatientRepository]
      val profile = await(patientRepository.findById(patientId)).get
      profile.firstName mustBe "Deleted"
      profile.lastName mustBe ""
      profile.phone mustBe None

      // The original email is anonymized away as part of deletion, so it no
      // longer resolves to any account at all — indistinguishable from a
      // wrong password, by design (lets it be reused for a fresh sign-up).
      val signInRes = route(app, FakeRequest(POST, "/api/auth/sign-in")
        .withJsonBody(Json.obj("email" -> "delete-patient1@example.com", "password" -> "correctPass123"))).get
      status(signInRes) mustBe UNAUTHORIZED
      (contentAsJson(signInRes) \ "code").as[String] mustBe "INVALID_CREDENTIALS"
    }

    "free up the email for a fresh sign-up" in {
      val token = signUp("delete-patient2@example.com", "patient", "correctPass123")
      status(deleteAccount(token, "correctPass123")) mustBe OK

      val resignupRes = route(app, FakeRequest(POST, "/api/auth/sign-up")
        .withJsonBody(Json.obj("email" -> "delete-patient2@example.com", "password" -> "newPass123", "role" -> "patient"))).get

      status(resignupRes) mustBe CREATED
    }

    "revoke existing sessions" in {
      val signUpRes = route(app, FakeRequest(POST, "/api/auth/sign-up")
        .withJsonBody(Json.obj("email" -> "delete-patient3@example.com", "password" -> "correctPass123", "role" -> "patient"))).get
      val accessToken = (contentAsJson(signUpRes) \ "accessToken").as[String]
      val refreshToken = (contentAsJson(signUpRes) \ "refreshToken").as[String]

      status(deleteAccount(accessToken, "correctPass123")) mustBe OK

      val refreshRes = route(app, FakeRequest(POST, "/api/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> refreshToken))).get
      status(refreshRes) mustBe UNAUTHORIZED
    }

    "cancel future active bookings on behalf of the patient" in {
      val patientToken = signUp("delete-patient4@example.com", "patient", "correctPass123")
      val patientId = ownId(patientToken, "/api/patients/dashboard")
      val surgeonToken = signUp("delete-surgeon4@example.com", "surgeon")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")

      val bookingRepository = app.injector.instanceOf[BookingRepository]
      val booking = await(bookingRepository.createBooking(
        enquiryId        = None,
        patientId        = patientId,
        surgeonId        = surgeonId,
        consultationType = ConsultationType.Virtual,
        scheduledAt      = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
        durationMinutes  = 60,
        fee              = BigDecimal(100)
      ))

      status(deleteAccount(patientToken, "correctPass123")) mustBe OK

      val updated = await(bookingRepository.findBookingById(booking.id)).get
      updated.status.entryName mustBe "cancelled_by_patient"
    }

    "reject an incorrect password" in {
      val token = signUp("delete-patient5@example.com", "patient", "correctPass123")

      val res = deleteAccount(token, "wrongPassword")

      status(res) mustBe UNAUTHORIZED
      val userRepository = app.injector.instanceOf[UserRepository]
      val stillActive = await(userRepository.findByEmail("delete-patient5@example.com")).get
      stillActive.isActive mustBe true
    }

    "reject a surgeon (not yet supported)" in {
      val token = signUp("delete-surgeon-reject@example.com", "surgeon", "correctPass123")

      val res = deleteAccount(token, "correctPass123")

      status(res) mustBe FORBIDDEN
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(DELETE, "/api/patients/account")
        .withJsonBody(Json.obj("password" -> "whatever"))).get

      status(res) mustBe UNAUTHORIZED
    }

    "reject a missing password" in {
      val token = signUp("delete-patient6@example.com", "patient")

      val res = route(app, FakeRequest(DELETE, "/api/patients/account")
        .withHeaders("Authorization" -> s"Bearer $token")
        .withJsonBody(Json.obj())).get

      status(res) mustBe BAD_REQUEST
    }
  }
}
