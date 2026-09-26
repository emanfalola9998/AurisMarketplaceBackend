// ─── app/co/auris/controllers/ContactController.scala ────────────────────────
//
// The public contact form (unauthenticated — anyone visiting the marketing
// site can submit it). Forwards to auris.email.supportEmail rather than
// storing submissions; there's no dashboard for reading them.

package co.auris.controllers

import co.auris.actions.RateLimitAction
import co.auris.models.{ApiError, ContactRequest}
import co.auris.services.NotificationService
import play.api.libs.json._
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContactController @Inject() (
                                    cc:                  ControllerComponents,
                                    rateLimitAction:      RateLimitAction,
                                    notificationService:  NotificationService
                                  )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private val MaxMessageLength = 5000

  // ─── POST /api/contact ─────────────────────────────────────────────────────

  def submit: Action[JsValue] = (Action(parse.json) andThen rateLimitAction("contactSubmit")).async { implicit request =>
    request.body.validate[ContactRequest] match {
      case JsError(errors) =>
        Future.successful(BadRequest(validationError(errors)))

      case JsSuccess(req, _) =>
        val name    = req.name.trim
        val email   = req.email.trim
        val message = req.message.trim

        if (name.isEmpty || email.isEmpty || message.isEmpty) {
          Future.successful(BadRequest(apiError("BAD_REQUEST", "name, email, and message are required.")))
        } else if (message.length > MaxMessageLength) {
          Future.successful(BadRequest(apiError("BAD_REQUEST", s"message must be $MaxMessageLength characters or fewer.")))
        } else {
          notificationService.sendContactMessage(name, email, req.subject.map(_.trim).filter(_.nonEmpty), message)
            .map(_ => Ok(Json.obj("message" -> "Thanks for reaching out — we'll be in touch soon.")))
        }
    }
  }

  private def apiError(code: String, message: String): JsValue =
    Json.toJson(ApiError(code, message))

  private def validationError(errors: scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])]): JsValue =
    Json.obj(
      "code"    -> "VALIDATION_ERROR",
      "message" -> "Request body is invalid.",
      "details" -> JsError.toJson(JsError(errors))
    )
}
