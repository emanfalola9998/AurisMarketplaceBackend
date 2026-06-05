// ─── app/co/auris/controllers/SurgeonController.scala ────────────────────────

package co.auris.controllers

import co.auris.actions.JwtAuthAction
import co.auris.models._
import co.auris.repositories.SurgeonRepository
import co.auris.services.{SurgeonError, SurgeonService}
import play.api.libs.json._
import play.api.mvc._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SurgeonController @Inject() (
                                    cc:                ControllerComponents,
                                    authAction:        JwtAuthAction,
                                    surgeonService:    SurgeonService,
                                    surgeonRepository: SurgeonRepository
                                  )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // ─── GET /api/surgeons ────────────────────────────────────────────────────

  def search: Action[AnyContent] = Action.async { implicit request =>
    val specialty = request.getQueryString("specialty")
    val city      = request.getQueryString("city")
    val query     = request.getQueryString("q")
    val page      = request.getQueryString("page").flatMap(_.toIntOption).getOrElse(1)
    val pageSize  = request.getQueryString("pageSize").flatMap(_.toIntOption).getOrElse(20)

    surgeonService.search(specialty, city, query, page, pageSize).map { page =>
      Ok(Json.toJson(page))
    }
  }

  // ─── GET /api/surgeons/:id ────────────────────────────────────────────────

  def findById(id: UUID): Action[AnyContent] = Action.async {
    surgeonService.findPublicProfile(id).map {
      case Left(SurgeonError.NotFound) | Left(SurgeonError.NotLive) =>
        NotFound(apiError("NOT_FOUND", "Surgeon not found."))
      case Left(other) =>
        InternalServerError(apiError("INTERNAL_ERROR", other.toString))
      case Right(profile) =>
        Ok(Json.toJson(profile))
    }
  }

  // ─── GET /api/surgeons/:id/reviews ───────────────────────────────────────

  def reviews(id: UUID): Action[AnyContent] = Action.async {
    surgeonRepository.findById(id).map {
      case None    => NotFound(apiError("NOT_FOUND", "Surgeon not found."))
      case Some(_) =>
        // TODO: wire to ReviewRepository once built
        Ok(Json.obj("items" -> JsArray(), "totalCount" -> 0, "page" -> 1, "pageSize" -> 20))
    }
  }

  // ─── POST /api/surgeons/apply ─────────────────────────────────────────────

  def apply: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireSurgeon {
      surgeonService.submitApplication(request.userId).map {
        case Left(SurgeonError.NotFound) =>
          NotFound(apiError("NOT_FOUND", "Surgeon profile not found."))
        case Left(SurgeonError.ProfileIncomplete) =>
          BadRequest(apiError("PROFILE_INCOMPLETE", "Complete your profile before applying."))
        case Left(SurgeonError.AlreadyApplied) =>
          Conflict(apiError("ALREADY_APPLIED", "An application is already pending review."))
        case Left(other) =>
          InternalServerError(apiError("INTERNAL_ERROR", other.toString))
        case Right(app) =>
          Created(Json.toJson(app))
      }
    }
  }

  // ─── PUT /api/surgeons/profile ────────────────────────────────────────────

  def updateProfile: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireSurgeon {
      val body = request.body
      val result = for {
        title             <- (body \ "title").asOpt[String].toRight("title")
        firstName         <- (body \ "firstName").asOpt[String].toRight("firstName")
        lastName          <- (body \ "lastName").asOpt[String].toRight("lastName")
        gmcNumber         <- (body \ "gmcNumber").asOpt[String].toRight("gmcNumber")
        specialty         <- (body \ "specialty").asOpt[String].toRight("specialty")
        hospital          <- (body \ "hospital").asOpt[String].toRight("hospital")
        city              <- (body \ "city").asOpt[String].toRight("city")
      } yield (title, firstName, lastName, gmcNumber, specialty, hospital, city)

      result match {
        case Left(field) =>
          Future.successful(BadRequest(apiError("VALIDATION_ERROR", s"Missing required field: $field")))

        case Right((title, firstName, lastName, gmcNumber, specialty, hospital, city)) =>
          val qualifications    = (body \ "qualifications").asOpt[List[String]].getOrElse(Nil)
          val medicalSchool     = (body \ "medicalSchool").asOpt[String]
          val graduationYear    = (body \ "graduationYear").asOpt[Short]
          val fellowships       = (body \ "fellowships").asOpt[String]
          val subspecialties    = (body \ "subspecialties").asOpt[List[String]].getOrElse(Nil)
          val address           = (body \ "address").asOpt[String]
          val yearsExperience   = (body \ "yearsExperience").asOpt[Short].getOrElse(0.toShort)
          val languages         = (body \ "languages").asOpt[List[String]].getOrElse(List("English"))
          val bio               = (body \ "bio").asOpt[String]
          val procedures        = (body \ "procedures").asOpt[List[String]].getOrElse(Nil)
          val consultFeeClinic  = (body \ "consultFeeClinic").asOpt[BigDecimal]
          val consultFeeVirtual = (body \ "consultFeeVirtual").asOpt[BigDecimal]
          val offersVirtual     = (body \ "offersVirtual").asOpt[Boolean].getOrElse(true)

          surgeonService.updateProfile(
            request.userId, title, firstName, lastName, gmcNumber,
            qualifications, medicalSchool, graduationYear, fellowships,
            specialty, subspecialties, hospital, city, address,
            yearsExperience, languages, bio, procedures,
            consultFeeClinic, consultFeeVirtual, offersVirtual
          ).map {
            case Left(SurgeonError.NotFound) =>
              NotFound(apiError("NOT_FOUND", "Surgeon profile not found."))
            case Left(other) =>
              InternalServerError(apiError("INTERNAL_ERROR", other.toString))
            case Right(profile) =>
              Ok(Json.toJson(profile))
          }
      }
    }
  }

  // ─── GET /api/surgeons/dashboard ──────────────────────────────────────────

  def dashboard: Action[AnyContent] = authAction.async { implicit request =>
    request.requireSurgeon {
      surgeonService.getDashboard(request.userId).map {
        case Left(SurgeonError.NotFound) =>
          NotFound(apiError("NOT_FOUND", "Surgeon profile not found."))
        case Left(other) =>
          InternalServerError(apiError("INTERNAL_ERROR", other.toString))
        case Right(profile) =>
          Ok(Json.toJson(profile))
      }
    }
  }

  // ─── POST /api/surgeons/profile/avatar ───────────────────────────────────
  // Multipart file upload — stub until storage is wired

  def uploadAvatar: Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    authAction(parse.multipartFormData).async { implicit request =>
      request.requireSurgeon {
        request.body.file("avatar") match {
          case None =>
            Future.successful(BadRequest(apiError("BAD_REQUEST", "No file provided.")))
          case Some(_) =>
            // TODO: wire to StorageService — upload to S3/local, then call surgeonRepository.updateAvatar
            Future.successful(Ok(Json.obj("avatarUrl" -> "/uploads/placeholder.jpg")))
        }
      }
    }

  // ─── PUT /api/surgeons/availability ──────────────────────────────────────
  // Stub — full availability management in next iteration

  def updateAvailability: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireSurgeon {
      // TODO: wire to SurgeonAvailabilityRepository
      Future.successful(Ok(Json.obj("message" -> "Availability updated.")))
    }
  }

  private def apiError(code: String, message: String): JsValue =
    Json.toJson(ApiError(code, message))
}