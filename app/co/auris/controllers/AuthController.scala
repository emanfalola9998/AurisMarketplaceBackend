// ─── app/co/auris/controllers/AuthController.scala ───────────────────────────
//
// HTTP layer for all authentication endpoints.
// Delegates all business logic to AuthService.
//
// Routes to add in conf/routes:
//
//   POST   /api/auth/sign-in                 controllers.AuthController.signIn
//   POST   /api/auth/sign-up                 controllers.AuthController.signUp
//   POST   /api/auth/refresh                 controllers.AuthController.refresh
//   POST   /api/auth/sign-out                controllers.AuthController.signOut
//   POST   /api/auth/sign-out-all            controllers.AuthController.signOutAll
//   GET    /api/auth/me                      controllers.AuthController.me
//   POST   /api/auth/verify-email            controllers.AuthController.verifyEmail
//   POST   /api/auth/forgot-password         controllers.AuthController.forgotPassword
//   POST   /api/auth/reset-password          controllers.AuthController.resetPassword

package co.auris.controllers

import co.auris.actions.JwtAuthAction
import co.auris.models._
import co.auris.repositories.{PatientRepository, SurgeonRepository}
import co.auris.services.{AuthError, AuthService}
import play.api.libs.json._
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject() (
                                 cc:                ControllerComponents,
                                 authAction:        JwtAuthAction,
                                 authService:       AuthService,
                                 patientRepository: PatientRepository,
                                 surgeonRepository: SurgeonRepository
                               )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // ─── POST /api/auth/sign-in ────────────────────────────────────────────────

  def signIn: Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body.validate[SignInRequest] match {
      case JsError(errors) =>
        Future.successful(BadRequest(validationError(errors)))

      case JsSuccess(req, _) =>
        val userAgent = request.headers.get("User-Agent")
        val ipAddress = request.remoteAddress

        authService.signIn(req.email, req.password, userAgent, Some(ipAddress)).flatMap {
          case Left(AuthError.InvalidCredentials) =>
            Future.successful(Unauthorized(apiError("INVALID_CREDENTIALS", "Incorrect email or password.")))

          case Left(AuthError.UserInactive) =>
            Future.successful(Forbidden(apiError("ACCOUNT_INACTIVE", "Your account has been deactivated.")))

          case Left(other) =>
            Future.successful(InternalServerError(apiError("INTERNAL_ERROR", other.toString)))

          case Right((user, tokens)) =>
            buildMeResponse(user).map { meResp =>
              Ok(Json.obj(
                "user"         -> meResp,
                "accessToken"  -> tokens.accessToken,
                "refreshToken" -> tokens.refreshToken,
                "tokenType"    -> "Bearer",
                "expiresIn"    -> tokens.expiresIn
              ))
            }
        }
    }
  }

  // ─── POST /api/auth/sign-up ────────────────────────────────────────────────

  def signUp: Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body.validate[SignUpRequest] match {
      case JsError(errors) =>
        Future.successful(BadRequest(validationError(errors)))

      case JsSuccess(req, _) =>
        if (req.role == UserRole.Admin) {
          // Admin accounts are provisioned directly in the database, never
          // through public sign-up — see the ops runbook. Without this,
          // anyone could self-register with "role": "admin" and get full
          // admin panel access; nothing else on this endpoint checked it.
          Future.successful(
            Forbidden(apiError("ROLE_NOT_ALLOWED", "Admin accounts cannot be created via sign-up."))
          )
        } else if (req.password.length < 8) {
          Future.successful(
            BadRequest(apiError("WEAK_PASSWORD", "Password must be at least 8 characters."))
          )
        } else {
          val userAgent = request.headers.get("User-Agent")
          val ipAddress = request.remoteAddress

          authService.signUp(req.email, req.password, req.role, userAgent, Some(ipAddress)).flatMap {
            case Left(AuthError.EmailAlreadyExists) =>
              Future.successful(Conflict(apiError("EMAIL_EXISTS", "An account with this email already exists.")))

            case Left(other) =>
              Future.successful(InternalServerError(apiError("INTERNAL_ERROR", other.toString)))

            case Right((user, tokens)) =>
              buildMeResponse(user).map { meResp =>
                Created(Json.obj(
                  "user"         -> meResp,
                  "accessToken"  -> tokens.accessToken,
                  "refreshToken" -> tokens.refreshToken,
                  "tokenType"    -> "Bearer",
                  "expiresIn"    -> tokens.expiresIn
                ))
              }
          }
        }
    }
  }

  // ─── POST /api/auth/refresh ────────────────────────────────────────────────

  def refresh: Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body.validate[RefreshRequest] match {
      case JsError(errors) =>
        Future.successful(BadRequest(validationError(errors)))

      case JsSuccess(req, _) =>
        val userAgent = request.headers.get("User-Agent")
        val ipAddress = request.remoteAddress

        authService.refresh(req.refreshToken, userAgent, Some(ipAddress)).flatMap {
          case Left(AuthError.InvalidToken) =>
            Future.successful(Unauthorized(apiError("INVALID_TOKEN", "Refresh token is invalid or expired.")))

          case Left(AuthError.UserInactive) =>
            Future.successful(Forbidden(apiError("ACCOUNT_INACTIVE", "Your account has been deactivated.")))

          case Left(other) =>
            Future.successful(InternalServerError(apiError("INTERNAL_ERROR", other.toString)))

          case Right((user, tokens)) =>
            buildMeResponse(user).map { meResp =>
              Ok(Json.obj(
                "user"         -> meResp,
                "accessToken"  -> tokens.accessToken,
                "refreshToken" -> tokens.refreshToken,
                "tokenType"    -> "Bearer",
                "expiresIn"    -> tokens.expiresIn
              ))
            }
        }
    }
  }

  // ─── POST /api/auth/sign-out ───────────────────────────────────────────────

  def signOut: Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body.validate[RefreshRequest] match {
      case JsError(_) =>
        Future.successful(BadRequest(apiError("BAD_REQUEST", "refreshToken is required.")))

      case JsSuccess(req, _) =>
        authService.signOut(req.refreshToken).map(_ => NoContent)
    }
  }

  // ─── POST /api/auth/sign-out-all ──────────────────────────────────────────

  def signOutAll: Action[AnyContent] = authAction.async { implicit request =>
    authService.signOutAll(request.userId).map(_ => NoContent)
  }

  // ─── GET /api/auth/me ─────────────────────────────────────────────────────

  def me: Action[AnyContent] = authAction.async { implicit request =>
    buildMeResponse(request.user).map(resp => Ok(resp))
  }

  // ─── POST /api/auth/verify-email ──────────────────────────────────────────

  def verifyEmail: Action[JsValue] = Action(parse.json).async { implicit request =>
    (request.body \ "token").asOpt[String] match {
      case None =>
        Future.successful(BadRequest(apiError("BAD_REQUEST", "token is required.")))

      case Some(token) =>
        authService.verifyEmail(token).map {
          case Left(AuthError.InvalidToken) =>
            BadRequest(apiError("INVALID_TOKEN", "The verification link is invalid or has expired."))
          case Left(_) =>
            InternalServerError(apiError("INTERNAL_ERROR", "Verification failed."))
          case Right(_) =>
            Ok(Json.obj("message" -> "Email verified successfully."))
        }
    }
  }

  // ─── POST /api/auth/forgot-password ───────────────────────────────────────

  def forgotPassword: Action[JsValue] = Action(parse.json).async { implicit request =>
    (request.body \ "email").asOpt[String] match {
      case None =>
        Future.successful(BadRequest(apiError("BAD_REQUEST", "email is required.")))

      case Some(email) =>
        // Always return 200 to prevent email enumeration
        authService.initiatePasswordReset(email).map { _ =>
          Ok(Json.obj("message" -> "If an account with that email exists, a reset link has been sent."))
        }
    }
  }

  // ─── POST /api/auth/reset-password ────────────────────────────────────────

  def resetPassword: Action[JsValue] = Action(parse.json).async { implicit request =>
    val tokenOpt    = (request.body \ "token").asOpt[String]
    val passwordOpt = (request.body \ "newPassword").asOpt[String]

    (tokenOpt, passwordOpt) match {
      case (None, _) =>
        Future.successful(BadRequest(apiError("BAD_REQUEST", "token is required.")))
      case (_, None) =>
        Future.successful(BadRequest(apiError("BAD_REQUEST", "newPassword is required.")))
      case (_, Some(p)) if p.length < 8 =>
        Future.successful(BadRequest(apiError("WEAK_PASSWORD", "Password must be at least 8 characters.")))

      case (Some(token), Some(newPassword)) =>
        authService.completePasswordReset(token, newPassword).map {
          case Left(AuthError.InvalidToken) =>
            BadRequest(apiError("INVALID_TOKEN", "The reset link is invalid or has expired."))
          case Left(_) =>
            InternalServerError(apiError("INTERNAL_ERROR", "Password reset failed."))
          case Right(_) =>
            Ok(Json.obj("message" -> "Password reset successfully. Please sign in with your new password."))
        }
    }
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private def buildMeResponse(user: User): Future[JsValue] = {
    val patientFuture = if (user.role == UserRole.Patient)
      patientRepository.findByUserId(user.id)
    else Future.successful(None)

    val surgeonFuture = if (user.role == UserRole.Surgeon)
      surgeonRepository.findByUserId(user.id)
    else Future.successful(None)

    for {
      patient <- patientFuture
      surgeon <- surgeonFuture
    } yield Json.toJson(UserResponse.from(user, patient, surgeon))
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