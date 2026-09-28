// ─── test/co/auris/controllers/AdminApplicationsSpec.scala ───────────────────
//
// Full-stack integration tests for the admin surgeon-application review
// endpoints: list, get, approve, reject, request-info.

package co.auris.controllers

import co.auris.models.UserRole
import co.auris.repositories.UserRepository
import co.auris.support.PlayIntegrationSpec
import org.mindrot.jbcrypt.BCrypt
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class AdminApplicationsSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def signUp(email: String, role: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

  // Admins aren't self-registrable via sign-up (see AuthControllerSpec's
  // "reject role: admin" regression test) — provision one directly, then
  // sign in normally, same pattern as AdminAnalyticsSpec.
  private def signUpAdminAndSignIn(email: String, password: String = "password123"): String = {
    val userRepository = app.injector.instanceOf[UserRepository]
    val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(4))
    await(userRepository.create(email, passwordHash, UserRole.Admin))

    val res = route(app, FakeRequest(POST, "/api/auth/sign-in")
      .withJsonBody(Json.obj("email" -> email, "password" -> password))).get
    status(res) mustBe OK
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def submitApplication(surgeonToken: String): UUID = {
    status(route(app, FakeRequest(PUT, "/api/surgeons/profile")
      .withHeaders("Authorization" -> s"Bearer $surgeonToken")
      .withJsonBody(Json.obj(
        "title" -> "Dr.", "firstName" -> "Jordan", "lastName" -> "Blake", "gmcNumber" -> "1234567",
        "specialty" -> "Plastic Surgery", "hospital" -> "Test Hospital", "city" -> "London"
      ))).get) mustBe OK

    val res = route(app, FakeRequest(POST, "/api/surgeons/apply")
      .withHeaders("Authorization" -> s"Bearer $surgeonToken")
      .withJsonBody(Json.obj())).get
    status(res) mustBe CREATED
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  "GET /api/admin/applications" should {
    "default to listing pending applications" in {
      val adminToken = signUpAdminAndSignIn("apps-list-admin1@example.com")
      val surgeonToken = signUp("apps-list-surgeon1@example.com", "surgeon")
      submitApplication(surgeonToken)

      val res = route(app, FakeRequest(GET, "/api/admin/applications")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe OK
      val json = contentAsJson(res)
      (json \ "status").as[String] mustBe "pending"
      (json \ "totalCount").as[Int] mustBe 1
    }

    "reject a non-admin" in {
      val patientToken = signUp("apps-list-nonadmin@example.com", "patient")

      val res = route(app, FakeRequest(GET, "/api/admin/applications")
        .withHeaders("Authorization" -> s"Bearer $patientToken")).get

      status(res) mustBe FORBIDDEN
    }
  }

  "GET /api/admin/applications/:id" should {
    "return the application with its surgeon profile" in {
      val adminToken = signUpAdminAndSignIn("apps-get-admin1@example.com")
      val surgeonToken = signUp("apps-get-surgeon1@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)

      val res = route(app, FakeRequest(GET, s"/api/admin/applications/$appId")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe OK
      val json = contentAsJson(res)
      (json \ "application" \ "id").as[String] mustBe appId.toString
      (json \ "surgeon").isDefined mustBe true
      (json \ "history").as[List[play.api.libs.json.JsObject]] mustBe empty
    }

    "return 404 for an application that doesn't exist" in {
      val adminToken = signUpAdminAndSignIn("apps-get-admin2@example.com")

      val res = route(app, FakeRequest(GET, s"/api/admin/applications/${UUID.randomUUID()}")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe NOT_FOUND
    }

    "includes a history entry for every review action, newest first, with the reviewer's email" in {
      val adminEmail = "apps-history-admin1@example.com"
      val adminToken = signUpAdminAndSignIn(adminEmail)
      val surgeonToken = signUp("apps-history-surgeon1@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)

      status(route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/request-info")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj("notes" -> "Please clarify your indemnity cover.", "flags" -> Json.arr("indemnity_unclear")))).get) mustBe OK

      status(route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/reject")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj("notes" -> "Never followed up."))).get) mustBe OK

      val res = route(app, FakeRequest(GET, s"/api/admin/applications/$appId")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe OK
      val history = (contentAsJson(res) \ "history").as[List[play.api.libs.json.JsObject]]
      history must have size 2

      // Newest first: the rejection is the most recent action.
      (history.head \ "action").as[String] mustBe "application_rejected"
      (history.head \ "actorEmail").as[String] mustBe adminEmail
      (history.head \ "metadata" \ "notes").as[String] mustBe "Never followed up."

      (history(1) \ "action").as[String] mustBe "application_more_info_requested"
      (history(1) \ "metadata" \ "flags").as[List[String]] mustBe List("indemnity_unclear")
    }
  }

  "PUT /api/admin/applications/:id/approve" should {
    "approve a pending application and make the surgeon's profile live" in {
      val adminToken = signUpAdminAndSignIn("apps-approve-admin1@example.com")
      val surgeonToken = signUp("apps-approve-surgeon1@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/approve")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe OK

      val dashboard = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get
      status(dashboard) mustBe OK
      (contentAsJson(dashboard) \ "profileLive").as[Boolean] mustBe true
    }

    "reject an application that's already approved" in {
      val adminToken = signUpAdminAndSignIn("apps-approve-admin2@example.com")
      val surgeonToken = signUp("apps-approve-surgeon2@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)
      status(route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/approve")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get) mustBe OK

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/approve")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe CONFLICT
    }

    "reject a non-admin" in {
      val surgeonToken = signUp("apps-approve-surgeon3@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/approve")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe FORBIDDEN
    }

    "return 404 for an application that doesn't exist" in {
      val adminToken = signUpAdminAndSignIn("apps-approve-admin4@example.com")

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/${UUID.randomUUID()}/approve")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe NOT_FOUND
    }
  }

  "PUT /api/admin/applications/:id/reject" should {
    "reject a pending application with notes and flags" in {
      val adminToken = signUpAdminAndSignIn("apps-reject-admin1@example.com")
      val surgeonToken = signUp("apps-reject-surgeon1@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/reject")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj("notes" -> "GMC number could not be verified.", "flags" -> Json.arr("unverifiable_gmc")))).get

      status(res) mustBe OK

      val getRes = route(app, FakeRequest(GET, s"/api/admin/applications/$appId")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get
      (contentAsJson(getRes) \ "application" \ "status").as[String] mustBe "rejected"
      (contentAsJson(getRes) \ "application" \ "reviewerNotes").as[String] mustBe "GMC number could not be verified."
    }

    "reject an application that's already rejected" in {
      val adminToken = signUpAdminAndSignIn("apps-reject-admin2@example.com")
      val surgeonToken = signUp("apps-reject-surgeon2@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)
      status(route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/reject")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get) mustBe OK

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/reject")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe CONFLICT
    }

    "reject a non-admin" in {
      val surgeonToken = signUp("apps-reject-surgeon3@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/reject")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe FORBIDDEN
    }
  }

  "PUT /api/admin/applications/:id/request-info" should {
    "move a pending application to more_info_required" in {
      val adminToken = signUpAdminAndSignIn("apps-info-admin1@example.com")
      val surgeonToken = signUp("apps-info-surgeon1@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/request-info")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj("notes" -> "Please upload a clearer copy of your indemnity certificate."))).get

      status(res) mustBe OK

      val getRes = route(app, FakeRequest(GET, s"/api/admin/applications/$appId")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get
      (contentAsJson(getRes) \ "application" \ "status").as[String] mustBe "more_info_required"
    }

    "return 404 for an application that doesn't exist" in {
      val adminToken = signUpAdminAndSignIn("apps-info-admin2@example.com")

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/${UUID.randomUUID()}/request-info")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe NOT_FOUND
    }

    "reject a non-admin" in {
      val surgeonToken = signUp("apps-info-surgeon3@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)

      val res = route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/request-info")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe FORBIDDEN
    }
  }

  "PUT /api/admin/surgeons/:id/suspend" should {
    "suspend an active surgeon, deactivate their account, and revoke sessions" in {
      val adminToken = signUpAdminAndSignIn("apps-suspend-admin1@example.com")
      val surgeonToken = signUp("apps-suspend-surgeon1@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)
      status(route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/approve")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get) mustBe OK

      val dashboard = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get
      val surgeonId = UUID.fromString((contentAsJson(dashboard) \ "id").as[String])

      val res = route(app, FakeRequest(PUT, s"/api/admin/surgeons/$surgeonId/suspend")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe OK

      // The surgeon's existing access token is still technically valid (JWTs
      // aren't revoked mid-life), but their refresh tokens are gone, so they
      // can no longer stay signed in — and a fresh sign-in must fail since
      // the account is inactive.
      val signInAttempt = route(app, FakeRequest(POST, "/api/auth/sign-in")
        .withJsonBody(Json.obj("email" -> "apps-suspend-surgeon1@example.com", "password" -> "password123"))).get
      status(signInAttempt) mustBe FORBIDDEN
    }

    "return 404 for a surgeon that doesn't exist" in {
      val adminToken = signUpAdminAndSignIn("apps-suspend-admin2@example.com")

      val res = route(app, FakeRequest(PUT, s"/api/admin/surgeons/${UUID.randomUUID()}/suspend")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe NOT_FOUND
    }

    "reject a non-admin" in {
      val surgeonToken = signUp("apps-suspend-surgeon3@example.com", "surgeon")
      val dashboard = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get
      val surgeonId = UUID.fromString((contentAsJson(dashboard) \ "id").as[String])

      val res = route(app, FakeRequest(PUT, s"/api/admin/surgeons/$surgeonId/suspend")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")
        .withJsonBody(Json.obj())).get

      status(res) mustBe FORBIDDEN
    }
  }

  "GET /api/admin/surgeons/:id/history" should {
    "includes a history entry once the surgeon has been suspended, with the admin's email" in {
      val adminEmail = "apps-surghist-admin1@example.com"
      val adminToken = signUpAdminAndSignIn(adminEmail)
      val surgeonToken = signUp("apps-surghist-surgeon1@example.com", "surgeon")
      val appId = submitApplication(surgeonToken)
      status(route(app, FakeRequest(PUT, s"/api/admin/applications/$appId/approve")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get) mustBe OK
      val dashboard = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get
      val surgeonId = UUID.fromString((contentAsJson(dashboard) \ "id").as[String])

      status(route(app, FakeRequest(PUT, s"/api/admin/surgeons/$surgeonId/suspend")
        .withHeaders("Authorization" -> s"Bearer $adminToken")
        .withJsonBody(Json.obj())).get) mustBe OK

      val res = route(app, FakeRequest(GET, s"/api/admin/surgeons/$surgeonId/history")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe OK
      val history = (contentAsJson(res) \ "history").as[List[play.api.libs.json.JsObject]]
      history must have size 1
      (history.head \ "action").as[String] mustBe "surgeon_suspended"
      (history.head \ "actorEmail").as[String] mustBe adminEmail
    }

    "returns an empty history for a surgeon that's never been suspended" in {
      val adminToken = signUpAdminAndSignIn("apps-surghist-admin2@example.com")
      val surgeonToken = signUp("apps-surghist-surgeon2@example.com", "surgeon")
      val dashboard = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get
      val surgeonId = UUID.fromString((contentAsJson(dashboard) \ "id").as[String])

      val res = route(app, FakeRequest(GET, s"/api/admin/surgeons/$surgeonId/history")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe OK
      (contentAsJson(res) \ "history").as[List[play.api.libs.json.JsObject]] mustBe empty
    }

    "return 404 for a surgeon that doesn't exist" in {
      val adminToken = signUpAdminAndSignIn("apps-surghist-admin3@example.com")

      val res = route(app, FakeRequest(GET, s"/api/admin/surgeons/${UUID.randomUUID()}/history")
        .withHeaders("Authorization" -> s"Bearer $adminToken")).get

      status(res) mustBe NOT_FOUND
    }

    "reject a non-admin" in {
      val surgeonToken = signUp("apps-surghist-surgeon3@example.com", "surgeon")
      val dashboard = route(app, FakeRequest(GET, "/api/surgeons/dashboard")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get
      val surgeonId = UUID.fromString((contentAsJson(dashboard) \ "id").as[String])

      val res = route(app, FakeRequest(GET, s"/api/admin/surgeons/$surgeonId/history")
        .withHeaders("Authorization" -> s"Bearer $surgeonToken")).get

      status(res) mustBe FORBIDDEN
    }
  }
}
