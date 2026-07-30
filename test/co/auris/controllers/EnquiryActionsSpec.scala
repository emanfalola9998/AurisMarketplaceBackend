// ─── test/co/auris/controllers/EnquiryActionsSpec.scala ──────────────────────
//
// Full-stack integration tests for the surgeon-side enquiry actions:
// suggest-time, cancel (a confirmed enquiry), notes, and the patientUserId
// enrichment on GET /api/enquiries for surgeons.

package co.auris.controllers

import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.util.UUID

class EnquiryActionsSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

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

  private def createEnquiry(patientToken: String, surgeonId: UUID): UUID = {
    val res = route(app, FakeRequest(POST, "/api/enquiries")
      .withHeaders("Authorization" -> s"Bearer $patientToken")
      .withJsonBody(Json.obj("surgeonId" -> surgeonId.toString, "procedureInterest" -> "Rhinoplasty"))).get
    status(res) mustBe CREATED
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def surgeonProfileId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  "PUT /api/enquiries/:id/suggest-time" should {
    "let the owning surgeon counter-propose a date/time on a pending enquiry" in {
      val surgeonToken = signUp("suggest-surgeon1@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("suggest-patient1@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/suggest-time")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj("preferredDate" -> "2026-09-01", "preferredTime" -> "14:00", "notes" -> "Does this work?"))).get

      status(res) mustBe OK
      val json = contentAsJson(res)
      (json \ "preferredDate").as[String] mustBe "2026-09-01"
      (json \ "preferredTime").as[String] mustBe "14:00:00"
      (json \ "status").as[String] mustBe "pending"
      (json \ "surgeonNotes").as[String] mustBe "Does this work?"
    }

    "reject a surgeon who doesn't own the enquiry" in {
      val surgeonToken = signUp("suggest-surgeon2@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val strangerToken = signUp("suggest-stranger2@example.com", "surgeon")
      val patientToken = signUp("suggest-patient2@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/suggest-time")
        .withHeaders("Authorization" -> s"Bearer $strangerToken")
        .withJsonBody(Json.obj("preferredDate" -> "2026-09-01", "preferredTime" -> "14:00"))).get

      status(res) mustBe FORBIDDEN
    }

    "reject an enquiry that's no longer pending" in {
      val surgeonToken = signUp("suggest-surgeon3@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("suggest-patient3@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)
      status(route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/accept")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj())).get) mustBe OK

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/suggest-time")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj("preferredDate" -> "2026-09-01", "preferredTime" -> "14:00"))).get

      status(res) mustBe CONFLICT
    }

    "reject a missing preferredTime" in {
      val surgeonToken = signUp("suggest-surgeon4@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("suggest-patient4@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/suggest-time")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj("preferredDate" -> "2026-09-01"))).get

      status(res) mustBe BAD_REQUEST
    }
  }

  "PUT /api/enquiries/:id/cancel" should {
    "let the owning surgeon cancel a confirmed enquiry" in {
      val surgeonToken = signUp("cancel-surgeon1@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("cancel-patient1@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)
      status(route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/accept")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj())).get) mustBe OK

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/cancel")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj("notes" -> "Schedule conflict"))).get

      status(res) mustBe OK
      (contentAsJson(res) \ "status").as[String] mustBe "cancelled"
    }

    "reject cancelling an enquiry that isn't confirmed" in {
      val surgeonToken = signUp("cancel-surgeon2@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("cancel-patient2@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/cancel")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe CONFLICT
    }
  }

  "PUT /api/enquiries/:id/notes" should {
    "let the owning surgeon add notes without changing status" in {
      val surgeonToken = signUp("notes-surgeon1@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("notes-patient1@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/notes")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj("notes" -> "Discussed aftercare in detail."))).get

      status(res) mustBe OK
      val json = contentAsJson(res)
      (json \ "surgeonNotes").as[String] mustBe "Discussed aftercare in detail."
      (json \ "status").as[String] mustBe "pending"
    }

    "reject empty notes" in {
      val surgeonToken = signUp("notes-surgeon2@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("notes-patient2@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/notes")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj("notes" -> "  "))).get

      status(res) mustBe BAD_REQUEST
    }

    "reject a surgeon who doesn't own the enquiry" in {
      val surgeonToken = signUp("notes-surgeon3@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val strangerToken = signUp("notes-stranger3@example.com", "surgeon")
      val patientToken = signUp("notes-patient3@example.com", "patient")
      val enquiryId = createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(PUT, s"/api/enquiries/$enquiryId/notes")
        .withHeaders("Authorization" -> s"Bearer $strangerToken")
        .withJsonBody(Json.obj("notes" -> "Sneaky note"))).get

      status(res) mustBe FORBIDDEN
    }
  }

  "GET /api/enquiries" should {
    "include patientUserId for a surgeon's own enquiries" in {
      val surgeonToken = signUp("enrich-surgeon1@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("enrich-patient1@example.com", "patient")
      val expectedPatientUserId = userId(patientToken)
      createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(GET, "/api/enquiries")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get

      status(res) mustBe OK
      val items = (contentAsJson(res) \ "items").as[List[play.api.libs.json.JsObject]]
      items must have size 1
      (items.head \ "patientUserId").as[String] mustBe expectedPatientUserId.toString
    }

    "not include patientUserId for a patient's own enquiries" in {
      val surgeonToken = signUp("enrich-surgeon2@example.com", "surgeon")
      val surgeonId = surgeonProfileId(surgeonToken)
      val patientToken = signUp("enrich-patient2@example.com", "patient")
      createEnquiry(patientToken, surgeonId)

      val res = route(app, FakeRequest(GET, "/api/enquiries")
        .withHeaders("Authorization" -> s"Bearer $patientToken")).get

      status(res) mustBe OK
      val items = (contentAsJson(res) \ "items").as[List[play.api.libs.json.JsObject]]
      items must have size 1
      (items.head \ "patientUserId").toOption mustBe None
    }
  }
}
