// ─── app/co/auris/controllers/BookingController.scala ────────────────────────

package co.auris.controllers

import co.auris.actions.JwtAuthAction
import co.auris.models._
import co.auris.services.{BookingError, BookingService}
import play.api.libs.json._
import play.api.mvc._

import java.time.{OffsetDateTime, LocalDate, LocalTime}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BookingController @Inject() (
                                    cc:             ControllerComponents,
                                    authAction:     JwtAuthAction,
                                    bookingService: BookingService
                                  )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // ─── POST /api/enquiries ──────────────────────────────────────────────────

  def createEnquiry: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requirePatient {
      val body = request.body
      (body \ "surgeonId").asOpt[String].flatMap(s => scala.util.Try(UUID.fromString(s)).toOption) match {
        case None =>
          Future.successful(BadRequest(apiError("BAD_REQUEST", "surgeonId is required.")))

        case Some(surgeonId) =>
          val consultationType = (body \ "consultationType").asOpt[String]
            .flatMap(s => ConsultationType.values.find(_.entryName == s))
            .getOrElse(ConsultationType.InClinic)

          val preferredDate = (body \ "preferredDate").asOpt[String]
            .flatMap(s => scala.util.Try(LocalDate.parse(s)).toOption)
          val preferredTime = (body \ "preferredTime").asOpt[String]
            .flatMap(s => scala.util.Try(LocalTime.parse(s)).toOption)

          bookingService.createEnquiry(
            patientUserId    = request.userId,
            surgeonId        = surgeonId,
            procedureInterest = (body \ "procedureInterest").asOpt[String],
            goals            = (body \ "goals").asOpt[String],
            previousSurgery  = (body \ "previousSurgery").asOpt[Boolean].getOrElse(false),
            previousDetails  = (body \ "previousDetails").asOpt[String],
            preferredDate    = preferredDate,
            preferredTime    = preferredTime,
            consultationType = consultationType,
            heardAbout       = (body \ "heardAbout").asOpt[String]
          ).map {
            case Left(BookingError.PatientNotFound) =>
              NotFound(apiError("NOT_FOUND", "Patient profile not found."))
            case Left(BookingError.SurgeonNotFound) =>
              NotFound(apiError("NOT_FOUND", "Surgeon not found."))
            case Left(other) =>
              InternalServerError(apiError("INTERNAL_ERROR", other.toString))
            case Right(enquiry) =>
              Created(Json.toJson(enquiry))
          }
      }
    }
  }

  // ─── GET /api/enquiries ───────────────────────────────────────────────────

  def listEnquiries: Action[AnyContent] = authAction.async { implicit request =>
    val status = request.getQueryString("status")
      .flatMap(s => EnquiryStatus.values.find(_.entryName == s))

    bookingService.listEnquiries(request.userId, request.user.role, status).map { enquiries =>
      Ok(Json.obj("items" -> Json.toJson(enquiries), "totalCount" -> enquiries.length))
    }
  }

  // ─── PUT /api/enquiries/:id/accept ───────────────────────────────────────

  def acceptEnquiry(id: UUID): Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireSurgeon {
      val notes = (request.body \ "notes").asOpt[String]
      bookingService.respondToEnquiry(request.userId, id, accept = true, notes).map {
        case Left(BookingError.NotFound)      => NotFound(apiError("NOT_FOUND", "Enquiry not found."))
        case Left(BookingError.Forbidden)     => Forbidden(apiError("FORBIDDEN", "Not your enquiry."))
        case Left(BookingError.InvalidStatus) => Conflict(apiError("INVALID_STATUS", "Enquiry is no longer pending."))
        case Left(other)                      => InternalServerError(apiError("INTERNAL_ERROR", other.toString))
        case Right(enquiry)                   => Ok(Json.toJson(enquiry))
      }
    }
  }

  // ─── PUT /api/enquiries/:id/decline ──────────────────────────────────────

  def declineEnquiry(id: UUID): Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireSurgeon {
      val notes = (request.body \ "notes").asOpt[String]
      bookingService.respondToEnquiry(request.userId, id, accept = false, notes).map {
        case Left(BookingError.NotFound)      => NotFound(apiError("NOT_FOUND", "Enquiry not found."))
        case Left(BookingError.Forbidden)     => Forbidden(apiError("FORBIDDEN", "Not your enquiry."))
        case Left(BookingError.InvalidStatus) => Conflict(apiError("INVALID_STATUS", "Enquiry is no longer pending."))
        case Left(other)                      => InternalServerError(apiError("INTERNAL_ERROR", other.toString))
        case Right(enquiry)                   => Ok(Json.toJson(enquiry))
      }
    }
  }

  // ─── POST /api/bookings ───────────────────────────────────────────────────

  def createBooking: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requirePatient {
      val body = request.body
      val parsed = for {
        surgeonId        <- (body \ "surgeonId").asOpt[String].flatMap(s => scala.util.Try(UUID.fromString(s)).toOption)
        scheduledAtStr   <- (body \ "scheduledAt").asOpt[String]
        scheduledAt      <- scala.util.Try(OffsetDateTime.parse(scheduledAtStr)).toOption
      } yield (surgeonId, scheduledAt)

      parsed match {
        case None =>
          Future.successful(BadRequest(apiError("BAD_REQUEST", "surgeonId and scheduledAt (ISO 8601) are required.")))

        case Some((surgeonId, scheduledAt)) =>
          val enquiryId = (body \ "enquiryId").asOpt[String]
            .flatMap(s => scala.util.Try(UUID.fromString(s)).toOption)
          val consultationType = (body \ "consultationType").asOpt[String]
            .flatMap(s => ConsultationType.values.find(_.entryName == s))
            .getOrElse(ConsultationType.InClinic)
          val durationMinutes = (body \ "durationMinutes").asOpt[Short].getOrElse(60.toShort)

          bookingService.createBooking(
            request.userId, surgeonId, enquiryId,
            consultationType, scheduledAt, durationMinutes
          ).map {
            case Left(BookingError.PatientNotFound) =>
              NotFound(apiError("NOT_FOUND", "Patient profile not found."))
            case Left(BookingError.SurgeonNotFound) =>
              NotFound(apiError("NOT_FOUND", "Surgeon not found."))
            case Left(other) =>
              InternalServerError(apiError("INTERNAL_ERROR", other.toString))
            case Right(booking) =>
              Created(Json.toJson(booking))
          }
      }
    }
  }

  // ─── GET /api/bookings ────────────────────────────────────────────────────

  def listBookings: Action[AnyContent] = authAction.async { implicit request =>
    val status = request.getQueryString("status")
      .flatMap(s => BookingStatus.values.find(_.entryName == s))

    bookingService.listBookings(request.userId, request.user.role, status).map { bookings =>
      Ok(Json.obj("items" -> Json.toJson(bookings), "totalCount" -> bookings.length))
    }
  }

  // ─── GET /api/bookings/:id ────────────────────────────────────────────────

  def findBooking(id: UUID): Action[AnyContent] = authAction.async { implicit request =>
    bookingService.findBooking(id, request.userId, request.user.role).map {
      case Left(BookingError.NotFound)  => NotFound(apiError("NOT_FOUND", "Booking not found."))
      case Left(BookingError.Forbidden) => Forbidden(apiError("FORBIDDEN", "Access denied."))
      case Left(other)                  => InternalServerError(apiError("INTERNAL_ERROR", other.toString))
      case Right(booking)               => Ok(Json.toJson(booking))
    }
  }

  // ─── PUT /api/bookings/:id/cancel ────────────────────────────────────────

  def cancelBooking(id: UUID): Action[JsValue] = authAction(parse.json).async { implicit request =>
    val note = (request.body \ "note").asOpt[String]
    bookingService.cancelBooking(id, request.userId, request.user.role, note).map {
      case Left(BookingError.NotFound)      => NotFound(apiError("NOT_FOUND", "Booking not found."))
      case Left(BookingError.Forbidden)     => Forbidden(apiError("FORBIDDEN", "Access denied."))
      case Left(BookingError.InvalidStatus) => Conflict(apiError("INVALID_STATUS", "Booking cannot be cancelled in its current state."))
      case Left(other)                      => InternalServerError(apiError("INTERNAL_ERROR", other.toString))
      case Right(booking)                   => Ok(Json.toJson(booking))
    }
  }

  // ─── POST /api/reviews ────────────────────────────────────────────────────

  def createReview: Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requirePatient {
      val body = request.body
      val parsed = for {
        bookingIdStr <- (body \ "bookingId").asOpt[String]
        bookingId    <- scala.util.Try(UUID.fromString(bookingIdStr)).toOption
        rating       <- (body \ "rating").asOpt[Short].filter(r => r >= 1 && r <= 5)
        reviewBody   <- (body \ "body").asOpt[String].filter(_.trim.nonEmpty)
      } yield (bookingId, rating, reviewBody)

      parsed match {
        case None =>
          Future.successful(BadRequest(apiError("BAD_REQUEST", "bookingId, rating (1-5), and body are required.")))

        case Some((bookingId, rating, reviewBody)) =>
          bookingService.createReview(
            patientUserId       = request.userId,
            bookingId           = bookingId,
            rating              = rating,
            ratingResults       = (body \ "ratingResults").asOpt[Short],
            ratingCommunication = (body \ "ratingCommunication").asOpt[Short],
            ratingAftercare     = (body \ "ratingAftercare").asOpt[Short],
            ratingValue         = (body \ "ratingValue").asOpt[Short],
            procedure           = (body \ "procedure").asOpt[String],
            body                = reviewBody
          ).map {
            case Left(BookingError.PatientNotFound)    => NotFound(apiError("NOT_FOUND", "Patient profile not found."))
            case Left(BookingError.NotFound)           => NotFound(apiError("NOT_FOUND", "Booking not found."))
            case Left(BookingError.Forbidden)          => Forbidden(apiError("FORBIDDEN", "This booking does not belong to you."))
            case Left(BookingError.BookingNotCompleted) => BadRequest(apiError("BOOKING_NOT_COMPLETED", "Reviews can only be submitted after a completed consultation."))
            case Left(BookingError.AlreadyReviewed)    => Conflict(apiError("ALREADY_REVIEWED", "You have already reviewed this consultation."))
            case Left(other)                           => InternalServerError(apiError("INTERNAL_ERROR", other.toString))
            case Right(review)                         => Created(Json.toJson(review))
          }
      }
    }
  }

  private def apiError(code: String, message: String): JsValue =
    Json.toJson(ApiError(code, message))
}