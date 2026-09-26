// ─── test/co/auris/controllers/ContactControllerSpec.scala ───────────────────
//
// Full-stack integration tests for the public contact form. NotificationService
// is overridden with a mock so this never sends a real email (mailer.mock =
// true would swallow it anyway, but this also lets us assert the message
// actually gets forwarded — the whole point of this endpoint existing).

package co.auris.controllers

import co.auris.services.NotificationService
import co.auris.support.PlayIntegrationSpec
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import scala.concurrent.Future

class ContactControllerSpec extends AnyWordSpec
  with Matchers
  with PlayIntegrationSpec
  with MockitoSugar
  with ArgumentMatchersSugar {

  private val notificationService: NotificationService = mock[NotificationService]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[NotificationService].toInstance(notificationService))
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(notificationService)
    when(notificationService.sendContactMessage(any[String], any[String], any[Option[String]], any[String]))
      .thenReturn(Future.successful(()))
    ()
  }

  "POST /api/contact" should {
    "forward a valid submission and return 200" in {
      val res = route(app, FakeRequest(POST, "/api/contact")
        .withJsonBody(Json.obj(
          "name"    -> "Charlotte Mitchell",
          "email"   -> "charlotte@example.com",
          "subject" -> "Billing question",
          "message" -> "I was charged twice for my consultation."
        ))).get

      status(res) mustBe OK
      verify(notificationService).sendContactMessage(
        "Charlotte Mitchell", "charlotte@example.com", Some("Billing question"), "I was charged twice for my consultation."
      )
    }

    "works without a subject" in {
      val res = route(app, FakeRequest(POST, "/api/contact")
        .withJsonBody(Json.obj(
          "name"    -> "James Harrison",
          "email"   -> "james@example.com",
          "message" -> "Just saying hello."
        ))).get

      status(res) mustBe OK
      verify(notificationService).sendContactMessage("James Harrison", "james@example.com", None, "Just saying hello.")
    }

    "trims whitespace and drops a blank subject" in {
      val res = route(app, FakeRequest(POST, "/api/contact")
        .withJsonBody(Json.obj(
          "name"    -> "  James Harrison  ",
          "email"   -> "  james@example.com ",
          "subject" -> "   ",
          "message" -> "  Hello  "
        ))).get

      status(res) mustBe OK
      verify(notificationService).sendContactMessage("James Harrison", "james@example.com", None, "Hello")
    }

    "reject a missing name" in {
      val res = route(app, FakeRequest(POST, "/api/contact")
        .withJsonBody(Json.obj("email" -> "james@example.com", "message" -> "Hello"))).get

      status(res) mustBe BAD_REQUEST
      verify(notificationService, never).sendContactMessage(any[String], any[String], any[Option[String]], any[String])
    }

    "reject a blank message" in {
      val res = route(app, FakeRequest(POST, "/api/contact")
        .withJsonBody(Json.obj("name" -> "James", "email" -> "james@example.com", "message" -> "   "))).get

      status(res) mustBe BAD_REQUEST
      verify(notificationService, never).sendContactMessage(any[String], any[String], any[Option[String]], any[String])
    }

    "reject a message over the length limit" in {
      val res = route(app, FakeRequest(POST, "/api/contact")
        .withJsonBody(Json.obj("name" -> "James", "email" -> "james@example.com", "message" -> ("x" * 5001)))).get

      status(res) mustBe BAD_REQUEST
      verify(notificationService, never).sendContactMessage(any[String], any[String], any[Option[String]], any[String])
    }
  }
}
