// ─── test/co/auris/controllers/BookingRescheduleSpec.scala ───────────────────
//
// Full-stack integration tests for PUT /api/bookings/:id/reschedule.

package co.auris.controllers

import co.auris.models.{Booking, ConsultationType}
import co.auris.repositories.BookingRepository
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

class BookingRescheduleSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private val targetDate = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7)
    .withHour(0).withMinute(0).withSecond(0).withNano(0)

  private def signUp(email: String, role: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def ownId(accessToken: String, path: String): UUID = {
    val res = route(app, FakeRequest(GET, path).withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

  private def createBooking(patientId: UUID, surgeonId: UUID, scheduledAt: OffsetDateTime): Booking = {
    val bookingRepository = app.injector.instanceOf[BookingRepository]
    await(bookingRepository.createBooking(
      enquiryId        = None,
      patientId        = patientId,
      surgeonId        = surgeonId,
      consultationType = ConsultationType.Virtual,
      scheduledAt      = scheduledAt,
      durationMinutes  = 60,
      fee              = BigDecimal(50)
    ))
  }

  private def reschedule(token: String, bookingId: UUID, newScheduledAt: OffsetDateTime) =
    route(app, FakeRequest(PUT, s"/api/bookings/$bookingId/reschedule")
      .withHeaders("Authorization" -> s"Bearer $token")
      .withJsonBody(Json.obj("scheduledAt" -> newScheduledAt.toString))).get

  "PUT /api/bookings/:id/reschedule" should {
    "let the owning patient move the booking to a new time" in {
      val surgeonToken = signUp("reschedule-surgeon1@example.com", "surgeon")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val patientToken = signUp("reschedule-patient1@example.com", "patient")
      val patientId = ownId(patientToken, "/api/patients/dashboard")

      val booking = createBooking(patientId, surgeonId, targetDate.plusHours(9))
      val newTime = targetDate.plusHours(11)

      val res = reschedule(patientToken, booking.id, newTime)

      status(res) mustBe OK
      OffsetDateTime.parse((contentAsJson(res) \ "scheduledAt").as[String]).isEqual(newTime) mustBe true
    }

    "let the owning surgeon move the booking to a new time" in {
      val surgeonToken = signUp("reschedule-surgeon2@example.com", "surgeon")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val patientToken = signUp("reschedule-patient2@example.com", "patient")
      val patientId = ownId(patientToken, "/api/patients/dashboard")

      val booking = createBooking(patientId, surgeonId, targetDate.plusHours(9))
      val newTime = targetDate.plusHours(14)

      val res = reschedule(surgeonToken, booking.id, newTime)

      status(res) mustBe OK
    }

    "reject a patient who doesn't own the booking" in {
      val surgeonToken = signUp("reschedule-surgeon3@example.com", "surgeon")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val ownerToken = signUp("reschedule-owner3@example.com", "patient")
      val ownerId = ownId(ownerToken, "/api/patients/dashboard")
      val strangerToken = signUp("reschedule-stranger3@example.com", "patient")

      val booking = createBooking(ownerId, surgeonId, targetDate.plusHours(9))

      val res = reschedule(strangerToken, booking.id, targetDate.plusHours(11))

      status(res) mustBe FORBIDDEN
    }

    "reject rescheduling a cancelled booking" in {
      val surgeonToken = signUp("reschedule-surgeon4@example.com", "surgeon")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val patientToken = signUp("reschedule-patient4@example.com", "patient")
      val patientId = ownId(patientToken, "/api/patients/dashboard")

      val booking = createBooking(patientId, surgeonId, targetDate.plusHours(9))
      val bookingRepository = app.injector.instanceOf[BookingRepository]
      await(bookingRepository.updateBookingStatus(booking.id, co.auris.models.BookingStatus.CancelledByPatient))

      val res = reschedule(patientToken, booking.id, targetDate.plusHours(11))

      status(res) mustBe CONFLICT
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_STATUS"
    }

    "reject a new time in the past" in {
      val surgeonToken = signUp("reschedule-surgeon5@example.com", "surgeon")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val patientToken = signUp("reschedule-patient5@example.com", "patient")
      val patientId = ownId(patientToken, "/api/patients/dashboard")

      val booking = createBooking(patientId, surgeonId, targetDate.plusHours(9))

      val res = reschedule(patientToken, booking.id, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))

      status(res) mustBe BAD_REQUEST
      (contentAsJson(res) \ "code").as[String] mustBe "INVALID_SCHEDULE"
    }

    "reject a new time that collides with another active booking for the same surgeon" in {
      val surgeonToken = signUp("reschedule-surgeon6@example.com", "surgeon")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val patientToken = signUp("reschedule-patient6@example.com", "patient")
      val patientId = ownId(patientToken, "/api/patients/dashboard")
      val otherPatientToken = signUp("reschedule-otherpatient6@example.com", "patient")
      val otherPatientId = ownId(otherPatientToken, "/api/patients/dashboard")

      createBooking(otherPatientId, surgeonId, targetDate.plusHours(12))
      val booking = createBooking(patientId, surgeonId, targetDate.plusHours(9))

      val res = reschedule(patientToken, booking.id, targetDate.plusHours(12))

      status(res) mustBe CONFLICT
      (contentAsJson(res) \ "code").as[String] mustBe "SLOT_UNAVAILABLE"
    }

    "allow rescheduling to a different, non-overlapping time on the same day another booking occupies" in {
      val surgeonToken = signUp("reschedule-surgeon7@example.com", "surgeon")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val patientToken = signUp("reschedule-patient7@example.com", "patient")
      val patientId = ownId(patientToken, "/api/patients/dashboard")
      val otherPatientToken = signUp("reschedule-otherpatient7@example.com", "patient")
      val otherPatientId = ownId(otherPatientToken, "/api/patients/dashboard")

      createBooking(otherPatientId, surgeonId, targetDate.plusHours(12))
      val booking = createBooking(patientId, surgeonId, targetDate.plusHours(9))

      val res = reschedule(patientToken, booking.id, targetDate.plusHours(15))

      status(res) mustBe OK
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(PUT, s"/api/bookings/${UUID.randomUUID()}/reschedule")
        .withJsonBody(Json.obj("scheduledAt" -> targetDate.plusHours(9).toString))).get

      status(res) mustBe UNAUTHORIZED
    }
  }
}
