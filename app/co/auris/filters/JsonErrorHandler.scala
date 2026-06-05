// ─── app/co/auris/filters/JsonErrorHandler.scala ──────────────────────────────
//
// Returns all error responses as JSON.
// Configured in application.conf: play.http.errorHandler = "co.auris.filters.JsonErrorHandler"

package co.auris.filters

import co.auris.models.ApiError
import play.api.http.HttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._

import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class JsonErrorHandler extends HttpErrorHandler {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    val (code, msg) = statusCode match {
      case 400 => ("BAD_REQUEST",  if (message.nonEmpty) message else "Bad request.")
      case 401 => ("UNAUTHORIZED", "Authentication required.")
      case 403 => ("FORBIDDEN",    "You do not have permission to perform this action.")
      case 404 => ("NOT_FOUND",    s"Resource not found: ${request.path}")
      case 405 => ("METHOD_NOT_ALLOWED", s"Method ${request.method} not allowed.")
      case _   => ("CLIENT_ERROR", message)
    }
    Future.successful(
      Status(statusCode)(Json.toJson(ApiError(code, msg)))
    )
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    // In production, log this properly and never leak the stack trace
    Future.successful(
      InternalServerError(Json.toJson(ApiError.InternalError))
    )
  }
}