// ─── app/co/auris/controllers/PatientController.scala ────────────────────────

package co.auris.controllers

import co.auris.actions.JwtAuthAction
import co.auris.models._
import co.auris.repositories.{PatientRepository, SurgeonRepository}
import co.auris.services.{AuthError, AuthService}
import play.api.libs.json._
import play.api.mvc._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PatientController @Inject() (
                                    cc:                ControllerComponents,
                                    authAction:        JwtAuthAction,
                                    patientRepository: PatientRepository,
                                    surgeonRepository: SurgeonRepository,
                                    authService:       AuthService
                                  )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // ─── PUT /api/patients/onboarding ────────────────────────────────────────

  def completeOnboarding: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requirePatient {
      patientRepository.findByUserId(request.userId).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Patient profile not found.")))

        case Some(profile) =>
          val body = request.body
          val firstName = (body \ "firstName").asOpt[String].getOrElse(profile.firstName)
          val lastName  = (body \ "lastName").asOpt[String].getOrElse(profile.lastName)
          val procedureInterests = (body \ "procedureInterests").asOpt[List[String]].getOrElse(Nil)
          val locationPreference = (body \ "city").asOpt[String]
          val consultPreference  = (body \ "consultType").asOpt[String]
            .flatMap(s => ConsultationType.values.find(_.entryName == s))
          val budgetRange = (body \ "budget").asOpt[String]
          val timeline    = (body \ "timeline").asOpt[String]

          patientRepository.updateOnboarding(
            profile.id, firstName, lastName, procedureInterests,
            locationPreference, consultPreference, budgetRange, timeline
          ).flatMap { _ =>
            patientRepository.findById(profile.id).map {
              case None    => InternalServerError(apiError("INTERNAL_ERROR", "Failed to load updated profile."))
              case Some(p) => Ok(Json.toJson(p))
            }
          }
      }
    }
  }

  // ─── PUT /api/patients/profile ───────────────────────────────────────────

  def updateProfile: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requirePatient {
      patientRepository.findByUserId(request.userId).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Patient profile not found.")))

        case Some(profile) =>
          val body = request.body
          val firstName = (body \ "firstName").asOpt[String].filter(_.trim.nonEmpty).getOrElse(profile.firstName)
          val lastName  = (body \ "lastName").asOpt[String].filter(_.trim.nonEmpty).getOrElse(profile.lastName)
          val dateOfBirth = (body \ "dateOfBirth").asOpt[String]
            .flatMap(s => scala.util.Try(java.time.LocalDate.parse(s)).toOption)
            .orElse(profile.dateOfBirth)
          val phone = (body \ "phone").asOpt[String].filter(_.trim.nonEmpty).orElse(profile.phone)

          patientRepository.updateProfile(profile.id, firstName, lastName, dateOfBirth, phone).flatMap { _ =>
            patientRepository.findById(profile.id).map {
              case None    => InternalServerError(apiError("INTERNAL_ERROR", "Failed to load updated profile."))
              case Some(p) => Ok(Json.toJson(p))
            }
          }
      }
    }
  }

  // ─── GET /api/patients/dashboard ─────────────────────────────────────────

  def dashboard: Action[AnyContent] = authAction.async { implicit request =>
    request.requirePatient {
      patientRepository.findByUserId(request.userId).map {
        case None         => NotFound(apiError("NOT_FOUND", "Patient profile not found."))
        case Some(profile) => Ok(Json.toJson(profile))
      }
    }
  }

  // ─── GET /api/patients/saved ──────────────────────────────────────────────

  def savedSurgeons: Action[AnyContent] = authAction.async { implicit request =>
    request.requirePatient {
      patientRepository.findByUserId(request.userId).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Patient profile not found.")))

        case Some(profile) =>
          patientRepository.savedSurgeonIds(profile.id).flatMap { ids =>
            Future.sequence(ids.map(surgeonRepository.findById)).map { results =>
              val surgeons = results.flatten.filter(_.profileLive).map(SurgeonSearchResult.from)
              Ok(Json.obj("items" -> Json.toJson(surgeons), "totalCount" -> surgeons.length))
            }
          }
      }
    }
  }

  // ─── POST /api/patients/saved/:surgeonId ─────────────────────────────────

  def saveSurgeon(surgeonId: UUID): Action[AnyContent] = authAction.async { implicit request =>
    request.requirePatient {
      patientRepository.findByUserId(request.userId).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Patient profile not found.")))
        case Some(profile) =>
          patientRepository.saveSurgeon(profile.id, surgeonId).map { _ =>
            Ok(Json.obj("message" -> "Surgeon saved.", "surgeonId" -> surgeonId.toString))
          }
      }
    }
  }

  // ─── DELETE /api/patients/saved/:surgeonId ────────────────────────────────

  def unsaveSurgeon(surgeonId: UUID): Action[AnyContent] = authAction.async { implicit request =>
    request.requirePatient {
      patientRepository.findByUserId(request.userId).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Patient profile not found.")))
        case Some(profile) =>
          patientRepository.unsaveSurgeon(profile.id, surgeonId).map { _ =>
            NoContent
          }
      }
    }
  }

  // ─── DELETE /api/patients/account ─────────────────────────────────────────

  def deleteAccount: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requirePatient {
      (request.body \ "password").asOpt[String].filter(_.nonEmpty) match {
        case None =>
          Future.successful(BadRequest(apiError("BAD_REQUEST", "password is required.")))

        case Some(password) =>
          authService.deleteAccount(request.userId, password).map {
            case Left(AuthError.InvalidCredentials) =>
              Unauthorized(apiError("INVALID_CREDENTIALS", "Incorrect password."))
            case Left(AuthError.UserNotFound) =>
              NotFound(apiError("NOT_FOUND", "Account not found."))
            case Left(AuthError.RoleNotSupported) =>
              Forbidden(apiError("ROLE_NOT_SUPPORTED", "Account deletion isn't available for this account type."))
            case Left(other) =>
              InternalServerError(apiError("INTERNAL_ERROR", other.toString))
            case Right(()) =>
              Ok(Json.obj("message" -> "Account deleted."))
          }
      }
    }
  }

  private def apiError(code: String, message: String): JsValue =
    Json.toJson(ApiError(code, message))
}