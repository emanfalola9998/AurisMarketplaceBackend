// ─── app/co/auris/actions/RateLimitAction.scala ───────────────────────────────
//
// Per-IP rate limiting for abuse-prone auth endpoints (credential stuffing on
// sign-in, spam registration, password-reset flooding). Limits are read from
// auris.rateLimit.<bucket>.{maxAttempts,windowMinutes} in application.conf.
//
// Usage in a controller:
//
//   def signIn: Action[JsValue] =
//     (Action(parse.json) andThen rateLimitAction("signIn")).async { implicit request => ... }

package co.auris.actions

import co.auris.models.ApiError
import co.auris.services.RateLimiter
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

@Singleton
class RateLimitAction @Inject() (
                                  rateLimiter: RateLimiter,
                                  config:      Configuration
                                )(implicit ec: ExecutionContext) {

  private val enabled = config.get[Boolean]("auris.rateLimit.enabled")

  def apply(bucket: String): ActionFilter[Request] = new ActionFilter[Request] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
      if (!enabled) {
        Future.successful(None)
      } else {
        val maxAttempts   = config.get[Int](s"auris.rateLimit.$bucket.maxAttempts")
        val windowMinutes = config.get[Int](s"auris.rateLimit.$bucket.windowMinutes")
        val key           = s"$bucket:${request.remoteAddress}"

        if (rateLimiter.tryAcquire(key, maxAttempts, windowMinutes.minutes)) {
          Future.successful(None)
        } else {
          Future.successful(Some(
            Results.TooManyRequests(Json.toJson(ApiError.RateLimited))
          ))
        }
      }
    }
  }
}
