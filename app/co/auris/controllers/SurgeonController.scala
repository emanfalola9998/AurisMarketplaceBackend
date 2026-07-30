// ─── app/co/auris/controllers/SurgeonController.scala ────────────────────────

package co.auris.controllers

import co.auris.actions.JwtAuthAction
import co.auris.models._
import co.auris.repositories.{NewAvailabilitySlot, SurgeonAvailabilityRepository, SurgeonRepository}
import co.auris.services.{AvailabilityService, StorageError, StorageService, SurgeonError, SurgeonService}
import play.api.libs.json._
import play.api.mvc._

import java.time.{LocalDate, LocalTime}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class SurgeonController @Inject() (
                                    cc:                          ControllerComponents,
                                    authAction:                  JwtAuthAction,
                                    surgeonService:              SurgeonService,
                                    surgeonRepository:           SurgeonRepository,
                                    availabilityRepository:      SurgeonAvailabilityRepository,
                                    availabilityService:         AvailabilityService,
                                    storageService:              StorageService
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

  // ─── GET /api/surgeons/:id/availability ──────────────────────────────────
  // Computed bookable slots for a public surgeon profile: weekly template
  // minus anything already booked or blocked. Defaults to the next 14 days.

  def availability(id: UUID): Action[AnyContent] = Action.async { implicit request =>
    def parseDate(s: String): Option[LocalDate] = Try(LocalDate.parse(s)).toOption

    val today    = LocalDate.now()
    val fromDate = request.getQueryString("from").flatMap(parseDate).getOrElse(today)
    val toDate   = request.getQueryString("to").flatMap(parseDate).getOrElse(fromDate.plusDays(13))
    val excludeBookingId = request.getQueryString("excludeBookingId").flatMap(s => Try(UUID.fromString(s)).toOption)

    if (toDate.isBefore(fromDate)) {
      Future.successful(BadRequest(apiError("VALIDATION_ERROR", "'to' must not be before 'from'.")))
    } else if (java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) > 60) {
      Future.successful(BadRequest(apiError("VALIDATION_ERROR", "Date range must not exceed 60 days.")))
    } else {
      surgeonRepository.findById(id).flatMap {
        case None    => Future.successful(NotFound(apiError("NOT_FOUND", "Surgeon not found.")))
        case Some(_) =>
          availabilityService.availableSlots(id, fromDate, toDate, excludeBookingId).map { days =>
            Ok(Json.obj("items" -> Json.toJson(days)))
          }
      }
    }
  }

  // ─── GET /api/surgeons/availability ──────────────────────────────────────
  // The authenticated surgeon's own weekly schedule template.

  def myAvailability: Action[AnyContent] = authAction.async { implicit request =>
    request.requireSurgeon {
      surgeonRepository.findByUserId(request.userId).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Surgeon profile not found.")))
        case Some(surgeon) =>
          availabilityRepository.listForSurgeon(surgeon.id).map { slots =>
            Ok(Json.obj("items" -> Json.toJson(slots)))
          }
      }
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

  def uploadAvatar: Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    authAction(parse.multipartFormData).async { implicit request =>
      request.requireSurgeon {
        request.body.file("avatar") match {
          case None =>
            Future.successful(BadRequest(apiError("BAD_REQUEST", "No file provided.")))

          case Some(filePart) =>
            storageService.store(filePart.ref, filePart.filename, filePart.contentType, "avatars").flatMap {
              case Left(StorageError.UnsupportedType) =>
                Future.successful(BadRequest(apiError("UNSUPPORTED_TYPE", "File type not allowed.")))
              case Left(StorageError.FileTooLarge) =>
                Future.successful(BadRequest(apiError("FILE_TOO_LARGE", "File exceeds the maximum allowed size.")))
              case Right(avatarUrl) =>
                surgeonRepository.findByUserId(request.userId).flatMap {
                  case None =>
                    Future.successful(NotFound(apiError("NOT_FOUND", "Surgeon profile not found.")))
                  case Some(surgeon) =>
                    surgeonRepository.updateAvatar(surgeon.id, avatarUrl).map { _ =>
                      Ok(Json.obj("avatarUrl" -> avatarUrl))
                    }
                }
            }
        }
      }
    }

  // ─── PUT /api/surgeons/availability ──────────────────────────────────────
  // Replaces the surgeon's entire weekly schedule with the given slots.

  def updateAvailability: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireSurgeon {
      val rawSlots = (request.body \ "slots").asOpt[List[JsValue]].getOrElse(Nil)

      val parsed: Either[String, List[NewAvailabilitySlot]] =
        rawSlots.foldLeft[Either[String, List[NewAvailabilitySlot]]](Right(Nil)) {
          case (Left(err), _) => Left(err)
          case (Right(acc), slot) =>
            val result = for {
              dayOfWeek <- (slot \ "dayOfWeek").asOpt[Short].filter(d => d >= 1 && d <= 7)
                             .toRight("each slot needs dayOfWeek between 1 (Mon) and 7 (Sun)")
              startTime <- (slot \ "startTime").asOpt[String].flatMap(s => scala.util.Try(LocalTime.parse(s)).toOption)
                             .toRight("each slot needs a valid startTime (HH:mm)")
              endTime   <- (slot \ "endTime").asOpt[String].flatMap(s => scala.util.Try(LocalTime.parse(s)).toOption)
                             .toRight("each slot needs a valid endTime (HH:mm)")
              _         <- Either.cond(startTime.isBefore(endTime), (), "startTime must be before endTime")
            } yield NewAvailabilitySlot(
              dayOfWeek     = dayOfWeek,
              startTime     = startTime,
              endTime       = endTime,
              bufferMinutes = (slot \ "bufferMinutes").asOpt[Short].getOrElse(30.toShort),
              isActive      = (slot \ "isActive").asOpt[Boolean].getOrElse(true)
            )
            result.map(s => acc :+ s)
        }

      parsed match {
        case Left(err) =>
          Future.successful(BadRequest(apiError("VALIDATION_ERROR", err)))

        case Right(slots) =>
          surgeonRepository.findByUserId(request.userId).flatMap {
            case None =>
              Future.successful(NotFound(apiError("NOT_FOUND", "Surgeon profile not found.")))
            case Some(surgeon) =>
              availabilityRepository.replaceForSurgeon(surgeon.id, slots).map { updated =>
                Ok(Json.obj("items" -> Json.toJson(updated)))
              }
          }
      }
    }
  }

  private def apiError(code: String, message: String): JsValue =
    Json.toJson(ApiError(code, message))
}