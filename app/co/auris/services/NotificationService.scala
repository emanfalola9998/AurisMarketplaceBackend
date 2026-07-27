// ─── app/co/auris/services/NotificationService.scala ──────────────────────────
//
// Transactional email. play.mailer.mock = true in dev/test, so sends are
// logged rather than actually dispatched until real SMTP creds are set.
// Failures here are swallowed by callers — a dropped notification should
// never fail the request that triggered it.

package co.auris.services

import play.api.Configuration
import play.api.libs.mailer.{Email, MailerClient}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationService @Inject() (
                                      mailerClient: MailerClient,
                                      config:       Configuration
                                    )(implicit ec: ExecutionContext) {

  private val fromAddress = config.get[String]("auris.email.fromAddress")
  private val fromName    = config.get[String]("auris.email.fromName")
  private val from        = s"$fromName <$fromAddress>"
  private val frontendUrl = config.get[String]("auris.frontendUrl")

  private def send(to: String, subject: String, bodyHtml: String): Future[Unit] =
    Future(mailerClient.send(Email(subject = subject, from = from, to = Seq(to), bodyHtml = Some(bodyHtml))))
      .map(_ => ())

  def sendEmailVerification(toEmail: String, token: String): Future[Unit] = {
    val link = s"$frontendUrl/auth?mode=verify-email&token=$token"
    send(
      toEmail,
      "Verify your Auris account",
      s"""<p>Welcome to Auris. Please confirm your email address to finish setting up your account:</p>
         |<p><a href="$link">Verify my email</a></p>""".stripMargin
    )
  }

  def sendPasswordReset(toEmail: String, token: String): Future[Unit] = {
    val link = s"$frontendUrl/auth?mode=reset-password&token=$token"
    send(
      toEmail,
      "Reset your Auris password",
      s"""<p>We received a request to reset your Auris password.</p>
         |<p><a href="$link">Choose a new password</a></p>
         |<p>If you didn't request this, you can safely ignore this email.</p>""".stripMargin
    )
  }

  def sendEnquiryReceived(surgeonEmail: String): Future[Unit] =
    send(
      surgeonEmail,
      "New patient enquiry on Auris",
      "<p>You've received a new patient enquiry. Sign in to your Auris dashboard to respond.</p>"
    )

  def sendEnquiryResponded(patientEmail: String, accepted: Boolean): Future[Unit] = {
    val verb = if (accepted) "accepted" else "declined"
    send(
      patientEmail,
      s"Your Auris enquiry was $verb",
      s"<p>The surgeon has $verb your consultation enquiry. Sign in to your Auris dashboard for details.</p>"
    )
  }

  def sendBookingConfirmed(patientEmail: String, surgeonEmail: String): Future[Unit] =
    for {
      _ <- send(
             patientEmail,
             "Your Auris consultation is confirmed",
             "<p>Your consultation booking has been confirmed. Sign in to your dashboard for details.</p>"
           )
      _ <- send(
             surgeonEmail,
             "New confirmed consultation",
             "<p>A consultation booking has been confirmed on your calendar. Sign in to your dashboard for details.</p>"
           )
    } yield ()
}
