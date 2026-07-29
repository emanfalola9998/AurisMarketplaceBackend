// ─── test/co/auris/controllers/PatientControllerSpec.scala ────────────────────
//
// Full-stack integration tests for PatientController: real routing, real JSON
// (de)serialization, real JwtAuthAction, real Postgres (auris_test — see
// application.test.conf and PlayIntegrationSpec).

package co.auris.controllers

import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.FakeRequest

class PatientControllerSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def signUpBody(email: String): JsValue =
    Json.obj("email" -> email, "password" -> "password123", "role" -> "patient")

  /** Signs up a fresh patient and returns their access token. */
  private def signUpPatient(email: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up").withJsonBody(signUpBody(email))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  "PUT /api/patients/profile" should {
    "update the patient's first name, last name, phone, and date of birth" in {
      val accessToken = signUpPatient("update-profile@example.com")

      val res = route(app, FakeRequest(PUT, "/api/patients/profile")
        .withHeaders("Authorization" -> s"Bearer $accessToken")
        .withJsonBody(Json.obj(
          "firstName"   -> "Charlotte",
          "lastName"    -> "Mitchell",
          "phone"       -> "+44 7700 900123",
          "dateOfBirth" -> "1990-05-15"
        ))).get

      status(res) mustBe OK
      val body = contentAsJson(res)
      (body \ "firstName").as[String] mustBe "Charlotte"
      (body \ "lastName").as[String] mustBe "Mitchell"
      (body \ "phone").as[String] mustBe "+44 7700 900123"
      (body \ "dateOfBirth").as[String] mustBe "1990-05-15"
    }

    "leave fields unchanged when they're omitted from the request body" in {
      val accessToken = signUpPatient("partial-update@example.com")

      val first = route(app, FakeRequest(PUT, "/api/patients/profile")
        .withHeaders("Authorization" -> s"Bearer $accessToken")
        .withJsonBody(Json.obj("firstName" -> "Sophie", "lastName" -> "Williams"))).get
      status(first) mustBe OK

      val second = route(app, FakeRequest(PUT, "/api/patients/profile")
        .withHeaders("Authorization" -> s"Bearer $accessToken")
        .withJsonBody(Json.obj("phone" -> "+44 7700 900456"))).get

      status(second) mustBe OK
      val body = contentAsJson(second)
      (body \ "firstName").as[String] mustBe "Sophie"
      (body \ "lastName").as[String] mustBe "Williams"
      (body \ "phone").as[String] mustBe "+44 7700 900456"
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(PUT, "/api/patients/profile")
        .withJsonBody(Json.obj("firstName" -> "Charlotte"))).get

      status(res) mustBe UNAUTHORIZED
    }

    "reject a surgeon trying to update a patient profile" in {
      val signUpRes = route(app, FakeRequest(POST, "/api/auth/sign-up")
        .withJsonBody(Json.obj("email" -> "surgeon-cant-update-patient@example.com", "password" -> "password123", "role" -> "surgeon"))).get
      status(signUpRes) mustBe CREATED
      val accessToken = (contentAsJson(signUpRes) \ "accessToken").as[String]

      val res = route(app, FakeRequest(PUT, "/api/patients/profile")
        .withHeaders("Authorization" -> s"Bearer $accessToken")
        .withJsonBody(Json.obj("firstName" -> "Charlotte"))).get

      status(res) mustBe FORBIDDEN
    }
  }
}
