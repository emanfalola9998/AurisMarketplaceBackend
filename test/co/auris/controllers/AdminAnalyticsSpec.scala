// ─── test/co/auris/controllers/AdminAnalyticsSpec.scala ──────────────────────
//
// Full-stack integration tests for GET /api/admin/analytics — pins down real
// aggregate counts, replacing what used to be a hardcoded all-zeros stub.

package co.auris.controllers

import co.auris.models.ConsultationType
import co.auris.repositories.{BookingRepository, SurgeonRepository}
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

class AdminAnalyticsSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def signUp(email: String, role: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

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

  "GET /api/admin/analytics" should {
    "return real aggregate counts" in {
      val adminToken = signUp("analytics-admin@example.com", "admin")

      val patient1Token = signUp("analytics-patient1@example.com", "patient")
      val patient2Token = signUp("analytics-patient2@example.com", "patient")
      val patientId = patientProfileId(patient1Token)

      val surgeonToken = signUp("analytics-surgeon@example.com", "surgeon")
      status(route(app, FakeRequest(PUT, "/api/surgeons/profile")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj(
          "title" -> "Dr.", "firstName" -> "Ana", "lastName" -> "Lytics", "gmcNumber" -> "1112223",
          "specialty" -> "Plastic Surgery", "hospital" -> "Test Hospital", "city" -> "London"
        ))).get) mustBe OK
      val surgeonId = surgeonProfileId(surgeonToken)

      // One pending application
      status(route(app, FakeRequest(POST, "/api/surgeons/apply")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj())).get) mustBe CREATED

      // Live (active) surgeon
      val surgeonRepository = app.injector.instanceOf[SurgeonRepository]
      await(surgeonRepository.setProfileLive(surgeonId, live = true))

      // One booking
      val bookingRepository = app.injector.instanceOf[BookingRepository]
      await(bookingRepository.createBooking(
        enquiryId        = None,
        patientId        = patientId,
        surgeonId        = surgeonId,
        consultationType = ConsultationType.Virtual,
        scheduledAt      = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
        durationMinutes  = 60,
        fee              = BigDecimal(100)
      ))

      val res = route(app, FakeRequest(GET, "/api/admin/analytics")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe OK
      val json = contentAsJson(res)
      (json \ "pendingApplications").as[Int] mustBe 1
      (json \ "activeSurgeons").as[Int] mustBe 1
      (json \ "totalBookings").as[Int] mustBe 1
      (json \ "totalPatients").as[Int] mustBe 2

      patient2Token must not be empty // keep the second sign-up meaningful to the totalPatients assertion
    }

    "reject a non-admin" in {
      val patientToken = signUp("analytics-nonadmin@example.com", "patient")

      val res = route(app, FakeRequest(GET, "/api/admin/analytics")
        .withHeaders("Authorization" -> s"Bearer $patientToken")).get

      status(res) mustBe FORBIDDEN
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(GET, "/api/admin/analytics")).get

      status(res) mustBe UNAUTHORIZED
    }
  }
}
