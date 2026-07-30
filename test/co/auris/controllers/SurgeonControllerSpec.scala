// ─── test/co/auris/controllers/SurgeonControllerSpec.scala ────────────────────
//
// Full-stack integration tests for SurgeonController: real routing, real JSON
// (de)serialization, real JwtAuthAction, real Postgres (auris_test — see
// application.test.conf and PlayIntegrationSpec).

package co.auris.controllers

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.Reviews
import co.auris.models.ConsultationType
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

class SurgeonControllerSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def signUpSurgeon(email: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> "surgeon"))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def signUpPatient(email: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> "patient"))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def ownId(accessToken: String, path: String): UUID = {
    val res = route(app, FakeRequest(GET, path).withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

  // Regression test: GET /api/surgeons/:id used to be declared before
  // GET /api/surgeons/dashboard in conf/routes. Play matches routes
  // top-to-bottom and a UUID-typed :id segment fails binding (400) on a
  // literal path instead of falling through to the next route — so
  // "dashboard" was being swallowed by :id and always 400ing. Nothing
  // caught this because no frontend code called the endpoint until much
  // later. This pins the fix down.
  "GET /api/surgeons/dashboard" should {
    "return the caller's own surgeon profile, not a 400 from being shadowed by GET /api/surgeons/:id" in {
      val accessToken = signUpSurgeon("dashboard-route-order@example.com")

      val res = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
        .withHeaders("Authorization" -> s"Bearer $accessToken")).get

      status(res) mustBe OK
      (contentAsJson(res) \ "code").asOpt[String] mustBe None
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(GET, "/api/surgeons/dashboard")).get

      status(res) mustBe UNAUTHORIZED
    }
  }

  "GET /api/surgeons/:id" should {
    "still correctly reject a non-UUID id with 400, now that dashboard is matched first" in {
      val res = route(app, FakeRequest(GET, "/api/surgeons/not-a-uuid")).get

      status(res) mustBe BAD_REQUEST
    }
  }

  // Regression test: this endpoint used to be a permanent stub returning
  // an empty list regardless of what reviews actually existed.
  "GET /api/surgeons/:id/reviews" should {
    "return a published review for a real surgeon" in {
      val surgeonToken = signUpSurgeon("reviews-surgeon1@example.com")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val patientToken = signUpPatient("reviews-patient1@example.com")
      val patientId = ownId(patientToken, "/api/patients/dashboard")

      val bookingRepository = app.injector.instanceOf[BookingRepository]
      val booking = await(bookingRepository.createBooking(
        enquiryId        = None,
        patientId        = patientId,
        surgeonId        = surgeonId,
        consultationType = ConsultationType.Virtual,
        scheduledAt      = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
        durationMinutes  = 60,
        fee              = BigDecimal(100)
      ))
      val review = await(bookingRepository.createReview(
        bookingId           = booking.id,
        patientId           = patientId,
        surgeonId           = surgeonId,
        rating               = 5,
        ratingResults        = Some(5),
        ratingCommunication  = Some(5),
        ratingAftercare      = Some(5),
        ratingValue          = Some(5),
        procedure            = Some("Rhinoplasty"),
        body                 = "Fantastic experience."
      ))
      await(db.run(Reviews.filter(_.id === review.id).map(_.isPublished).update(true)))

      val res = route(app, FakeRequest(GET, s"/api/surgeons/$surgeonId/reviews")).get

      status(res) mustBe OK
      val json = contentAsJson(res)
      (json \ "totalCount").as[Long] mustBe 1L
      val items = (json \ "items").as[List[play.api.libs.json.JsObject]]
      items must have size 1
      (items.head \ "body").as[String] mustBe "Fantastic experience."
    }

    "exclude unpublished reviews" in {
      val surgeonToken = signUpSurgeon("reviews-surgeon2@example.com")
      val surgeonId = ownId(surgeonToken, "/api/surgeons/dashboard")
      val patientToken = signUpPatient("reviews-patient2@example.com")
      val patientId = ownId(patientToken, "/api/patients/dashboard")

      val bookingRepository = app.injector.instanceOf[BookingRepository]
      val booking = await(bookingRepository.createBooking(
        enquiryId        = None,
        patientId        = patientId,
        surgeonId        = surgeonId,
        consultationType = ConsultationType.Virtual,
        scheduledAt      = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
        durationMinutes  = 60,
        fee              = BigDecimal(100)
      ))
      await(bookingRepository.createReview(
        bookingId           = booking.id,
        patientId           = patientId,
        surgeonId           = surgeonId,
        rating               = 4,
        ratingResults        = None,
        ratingCommunication  = None,
        ratingAftercare      = None,
        ratingValue          = None,
        procedure            = None,
        body                 = "Not yet moderated."
      ))

      val res = route(app, FakeRequest(GET, s"/api/surgeons/$surgeonId/reviews")).get

      status(res) mustBe OK
      (contentAsJson(res) \ "totalCount").as[Long] mustBe 0L
    }

    "return 404 for a surgeon id that doesn't exist" in {
      val res = route(app, FakeRequest(GET, s"/api/surgeons/${UUID.randomUUID()}/reviews")).get

      status(res) mustBe NOT_FOUND
    }
  }
}
