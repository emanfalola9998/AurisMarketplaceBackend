// ─── app/co/auris/actions/JwtAuthAction.scala ────────────────────────────────
//
// A Play ActionBuilder that:
//   1. Extracts the Bearer token from the Authorization header
//   2. Validates and decodes the JWT using the configured secret
//   3. Loads the User from the database
//   4. Injects an AuthenticatedRequest[A] into the controller
//
// Usage in controllers:
//
//   def myEndpoint = authAction.async { implicit request =>
//     val user: User = request.user
//     Future.successful(Ok(Json.toJson(UserResponse.from(user, ...))))
//   }
//
//   def patientOnly = authAction.async { implicit request =>
//     request.requirePatient {
//       Future.successful(Ok("patient content"))
//     }
//   }

package co.auris.actions

import co.auris.models.{ApiError, User, UserRole}
import co.auris.repositories.UserRepository
import co.auris.services.JwtService
import play.api.libs.json.Json
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

// ─── Authenticated request ────────────────────────────────────────────────────

class AuthenticatedRequest[A](
                               val user:    User,
                               val request: Request[A]
                             ) extends WrappedRequest[A](request) {

  def userId: java.util.UUID = user.id

  def isPatient: Boolean = user.role == UserRole.Patient
  def isSurgeon: Boolean = user.role == UserRole.Surgeon
  def isAdmin:   Boolean = user.role == UserRole.Admin

  /** Runs f only if the user is a patient, otherwise returns 403. */
  def requirePatient(f: => Future[Result]): Future[Result] =
    if (isPatient) f
    else Future.successful(Results.Forbidden(Json.toJson(ApiError.Forbidden)))

  /** Runs f only if the user is a surgeon, otherwise returns 403. */
  def requireSurgeon(f: => Future[Result]): Future[Result] =
    if (isSurgeon) f
    else Future.successful(Results.Forbidden(Json.toJson(ApiError.Forbidden)))

  /** Runs f only if the user is an admin, otherwise returns 403. */
  def requireAdmin(f: => Future[Result]): Future[Result] =
    if (isAdmin) f
    else Future.successful(Results.Forbidden(Json.toJson(ApiError.Forbidden)))
}

// ─── JwtAuthAction ────────────────────────────────────────────────────────────

class JwtAuthAction @Inject() (
                                defaultParser:  BodyParsers.Default,
                                jwtService:     JwtService,
                                userRepository: UserRepository
                              )(implicit ec: ExecutionContext)
  extends ActionBuilder[AuthenticatedRequest, AnyContent]
    with ActionRefiner[Request, AuthenticatedRequest] {

  override protected def executionContext: ExecutionContext = ec
  override def parser: BodyParser[AnyContent]              = defaultParser

  override protected def refine[A](
                                    request: Request[A]
                                  ): Future[Either[Result, AuthenticatedRequest[A]]] = {

    extractToken(request) match {
      case None =>
        Future.successful(Left(
          Results.Unauthorized(Json.toJson(ApiError.Unauthorized))
        ))

      case Some(token) =>
        jwtService.validateAccessToken(token) match {
          case Left(_) =>
            Future.successful(Left(
              Results.Unauthorized(Json.toJson(ApiError.InvalidToken))
            ))

          case Right(userId) =>
            userRepository.findById(userId).map {
              case None =>
                Left(Results.Unauthorized(Json.toJson(ApiError.Unauthorized)))

              case Some(user) if !user.isActive =>
                Left(Results.Forbidden(Json.toJson(ApiError.Forbidden)))

              case Some(user) =>
                Right(new AuthenticatedRequest(user, request))
            }
        }
    }
  }

  private def extractToken(request: RequestHeader): Option[String] =
    request.headers
      .get("Authorization")
      .collect { case s if s.startsWith("Bearer ") => s.drop(7).trim }
      .filter(_.nonEmpty)
}