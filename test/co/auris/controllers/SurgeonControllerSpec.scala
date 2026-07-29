// ─── test/co/auris/controllers/SurgeonControllerSpec.scala ────────────────────
//
// Full-stack integration tests for SurgeonController: real routing, real JSON
// (de)serialization, real JwtAuthAction, real Postgres (auris_test — see
// application.test.conf and PlayIntegrationSpec).

package co.auris.controllers

import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

class SurgeonControllerSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def signUpSurgeon(email: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> "surgeon"))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

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
}
