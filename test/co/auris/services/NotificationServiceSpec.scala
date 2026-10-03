// ─── test/co/auris/services/NotificationServiceSpec.scala ─────────────────────

package co.auris.services

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationServiceSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with ArgumentMatchersSugar {

  private val baseConfig: Map[String, Any] = Map(
    "auris.email.fromAddress" -> "noreply@auris.co",
    "auris.email.fromName"    -> "Auris",
    "auris.email.supportEmail" -> "support@auris.co",
    "auris.frontendUrl"       -> "http://localhost:5173"
  )

  private def config(emailNotifications: Boolean): Configuration =
    Configuration.from(baseConfig + ("auris.features.emailNotifications" -> emailNotifications))

  "sendPasswordReset" should {
    "dispatch via the mailer client when emailNotifications is enabled" in {
      val mailerClient = mock[MailerClient]
      val service = new NotificationService(mailerClient, config(emailNotifications = true))

      service.sendPasswordReset("patient@example.com", "reset-token").futureValue

      verify(mailerClient).send(any[Email])
    }

    "skip the mailer client entirely when emailNotifications is disabled" in {
      val mailerClient = mock[MailerClient]
      val service = new NotificationService(mailerClient, config(emailNotifications = false))

      service.sendPasswordReset("patient@example.com", "reset-token").futureValue

      verify(mailerClient, never).send(any[Email])
    }
  }
}
