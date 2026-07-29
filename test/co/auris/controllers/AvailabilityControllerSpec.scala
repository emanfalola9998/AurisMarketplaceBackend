// ─── test/co/auris/controllers/AvailabilityControllerSpec.scala ──────────────
//
// Full-stack integration tests for the computed-availability endpoints:
// GET /api/surgeons/:id/availability (public, computed) and
// GET /api/surgeons/availability (the surgeon's own weekly template).

package co.auris.controllers

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.SurgeonBlockedSlots
import co.auris.models.ConsultationType
import co.auris.repositories.BookingRepository
import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime, ZoneOffset}
import java.util.UUID

class AvailabilityControllerSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  // A date comfortably in the future (a week out) so slots never trip the
  // "must be after now" filter regardless of when the suite runs.
  private val targetDate = LocalDate.now().plusDays(7)
  private val isoDayOfWeek = targetDate.getDayOfWeek.getValue
  private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

  private def t(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(TimeFmt)

  private def signUp(email: String, role: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def ownSurgeonId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def ownPatientId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/patients/dashboard")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def setWeeklyTemplate(accessToken: String): Unit = {
    val res = route(app, FakeRequest(PUT, "/api/surgeons/availability")
      .withHeaders("Authorization" -> s"Bearer $accessToken")
      .withJsonBody(Json.obj("slots" -> Json.arr(
        Json.obj("dayOfWeek" -> isoDayOfWeek, "startTime" -> "09:00", "endTime" -> "11:00", "bufferMinutes" -> 0)
      )))).get
    status(res) mustBe OK
    ()
  }

  "GET /api/surgeons/:id/availability" should {
    "return an empty slot list for every date when the surgeon has no weekly template" in {
      val token = signUp("no-template-surgeon@example.com", "surgeon")
      val surgeonId = ownSurgeonId(token)

      val res = route(app, FakeRequest(GET, s"/api/surgeons/$surgeonId/availability?from=$targetDate&to=$targetDate")).get

      status(res) mustBe OK
      val items = (contentAsJson(res) \ "items").as[List[play.api.libs.json.JsObject]]
      items must have size 1
      (items.head \ "slots").as[List[String]] mustBe empty
    }

    "return computed slots based on the surgeon's weekly template" in {
      val token = signUp("templated-surgeon@example.com", "surgeon")
      val surgeonId = ownSurgeonId(token)
      setWeeklyTemplate(token)

      val res = route(app, FakeRequest(GET, s"/api/surgeons/$surgeonId/availability?from=$targetDate&to=$targetDate")).get

      status(res) mustBe OK
      val items = (contentAsJson(res) \ "items").as[List[play.api.libs.json.JsObject]]
      items must have size 1
      (items.head \ "date").as[String] mustBe targetDate.toString
      (items.head \ "slots").as[List[String]] mustBe List(t(9, 0), t(10, 0))
    }

    "exclude a slot that already has an active booking" in {
      val surgeonToken = signUp("booked-surgeon@example.com", "surgeon")
      val surgeonId = ownSurgeonId(surgeonToken)
      setWeeklyTemplate(surgeonToken)

      val patientToken = signUp("booked-patient@example.com", "patient")
      val patientId = ownPatientId(patientToken)

      val bookingRepository = app.injector.instanceOf[BookingRepository]
      val scheduledAt = targetDate.atTime(9, 0).atOffset(ZoneOffset.UTC)
      await(bookingRepository.createBooking(
        enquiryId        = None,
        patientId        = patientId,
        surgeonId        = surgeonId,
        consultationType = ConsultationType.Virtual,
        scheduledAt      = scheduledAt,
        durationMinutes  = 60,
        fee              = BigDecimal(50)
      ))

      val res = route(app, FakeRequest(GET, s"/api/surgeons/$surgeonId/availability?from=$targetDate&to=$targetDate")).get

      status(res) mustBe OK
      val items = (contentAsJson(res) \ "items").as[List[play.api.libs.json.JsObject]]
      (items.head \ "slots").as[List[String]] mustBe List(t(10, 0))
    }

    "exclude a slot that is blocked by the surgeon" in {
      val token = signUp("blocked-surgeon@example.com", "surgeon")
      val surgeonId = ownSurgeonId(token)
      setWeeklyTemplate(token)

      val blockedAt = targetDate.atTime(10, 0).atOffset(ZoneOffset.UTC)
      await(db.run(SurgeonBlockedSlots += co.auris.models.SurgeonBlockedSlot(
        id           = UUID.randomUUID(),
        surgeonId    = surgeonId,
        blockedAt    = blockedAt,
        durationMins = 60,
        reason       = Some("Annual leave")
      )))

      val res = route(app, FakeRequest(GET, s"/api/surgeons/$surgeonId/availability?from=$targetDate&to=$targetDate")).get

      status(res) mustBe OK
      val items = (contentAsJson(res) \ "items").as[List[play.api.libs.json.JsObject]]
      (items.head \ "slots").as[List[String]] mustBe List(t(9, 0))
    }

    "return 404 for a surgeon id that doesn't exist" in {
      val res = route(app, FakeRequest(GET, s"/api/surgeons/${UUID.randomUUID()}/availability")).get

      status(res) mustBe NOT_FOUND
    }

    "reject a range where 'to' is before 'from'" in {
      val token = signUp("range-surgeon@example.com", "surgeon")
      val surgeonId = ownSurgeonId(token)

      val res = route(app, FakeRequest(GET,
        s"/api/surgeons/$surgeonId/availability?from=$targetDate&to=${targetDate.minusDays(1)}")).get

      status(res) mustBe BAD_REQUEST
    }
  }

  "GET /api/surgeons/availability" should {
    "return the caller's own weekly template" in {
      val token = signUp("own-schedule-surgeon@example.com", "surgeon")
      setWeeklyTemplate(token)

      val res = route(app, FakeRequest(GET, "/api/surgeons/availability")
        .withHeaders("Authorization" -> s"Bearer $token")).get

      status(res) mustBe OK
      val items = (contentAsJson(res) \ "items").as[List[play.api.libs.json.JsObject]]
      items must have size 1
      (items.head \ "dayOfWeek").as[Int] mustBe isoDayOfWeek
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(GET, "/api/surgeons/availability")).get

      status(res) mustBe UNAUTHORIZED
    }

    "reject a patient trying to read a surgeon schedule" in {
      val token = signUp("own-schedule-patient@example.com", "patient")

      val res = route(app, FakeRequest(GET, "/api/surgeons/availability")
        .withHeaders("Authorization" -> s"Bearer $token")).get

      status(res) mustBe FORBIDDEN
    }
  }

  private def await[T](f: scala.concurrent.Future[T]): T =
    scala.concurrent.Await.result(f, scala.concurrent.duration.DurationInt(10).seconds)
}
