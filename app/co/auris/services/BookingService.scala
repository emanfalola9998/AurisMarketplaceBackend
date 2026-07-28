// ─── app/co/auris/services/BookingService.scala ───────────────────────────────

package co.auris.services

import co.auris.models._
import co.auris.repositories.{BookingRepository, PatientRepository, SurgeonRepository, UserRepository}

import java.time.{LocalDate, LocalTime, OffsetDateTime}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait BookingError
object BookingError {
  case object NotFound              extends BookingError
  case object Forbidden             extends BookingError
  case object AlreadyReviewed       extends BookingError
  case object BookingNotCompleted   extends BookingError
  case object InvalidStatus         extends BookingError
  case object SurgeonNotFound       extends BookingError
  case object PatientNotFound       extends BookingError
  case class  Unexpected(msg: String) extends BookingError
}

final case class BookingWithPayment(booking: Booking, stripeClientSecret: String)

@Singleton
class BookingService @Inject() (
                                 bookingRepository:   BookingRepository,
                                 patientRepository:   PatientRepository,
                                 surgeonRepository:   SurgeonRepository,
                                 userRepository:      UserRepository,
                                 notificationService: NotificationService,
                                 paymentService:      PaymentService
                               )(implicit ec: ExecutionContext) {

  private def notify(f: => Future[Unit]): Future[Unit] = f.recover { case _ => () }

  // ─── Enquiries ─────────────────────────────────────────────────────────────

  def createEnquiry(
                     patientUserId:    UUID,
                     surgeonId:        UUID,
                     procedureInterest: Option[String],
                     goals:            Option[String],
                     previousSurgery:  Boolean,
                     previousDetails:  Option[String],
                     preferredDate:    Option[LocalDate],
                     preferredTime:    Option[LocalTime],
                     consultationType: ConsultationType,
                     heardAbout:       Option[String]
                   ): Future[Either[BookingError, Enquiry]] =
    for {
      patientOpt <- patientRepository.findByUserId(patientUserId)
      surgeonOpt <- surgeonRepository.findById(surgeonId)
      result <- (patientOpt, surgeonOpt) match {
        case (None, _)    => Future.successful(Left(BookingError.PatientNotFound))
        case (_, None)    => Future.successful(Left(BookingError.SurgeonNotFound))
        case (Some(patient), Some(surgeon)) =>
          val fee = consultationType match {
            case ConsultationType.InClinic => surgeon.consultFeeClinic.getOrElse(BigDecimal(0))
            case ConsultationType.Virtual  => surgeon.consultFeeVirtual.getOrElse(BigDecimal(0))
          }
          bookingRepository.createEnquiry(
            patient.id, surgeonId, procedureInterest, goals,
            previousSurgery, previousDetails, preferredDate, preferredTime,
            consultationType, fee, heardAbout
          ).flatMap { e =>
            notify(
              userRepository.findById(surgeon.userId).flatMap {
                case Some(u) => notificationService.sendEnquiryReceived(u.email)
                case None    => Future.successful(())
              }
            ).map(_ => Right(e))
          }
      }
    } yield result

  def listEnquiries(userId: UUID, role: UserRole, status: Option[EnquiryStatus]): Future[List[Enquiry]] =
    role match {
      case UserRole.Patient =>
        patientRepository.findByUserId(userId).flatMap {
          case None    => Future.successful(Nil)
          case Some(p) => bookingRepository.listEnquiriesForPatient(p.id, status)
        }
      case UserRole.Surgeon =>
        surgeonRepository.findByUserId(userId).flatMap {
          case None    => Future.successful(Nil)
          case Some(s) => bookingRepository.listEnquiriesForSurgeon(s.id, status)
        }
      case _ => Future.successful(Nil)
    }

  def respondToEnquiry(
                        surgeonUserId: UUID,
                        enquiryId:     UUID,
                        accept:        Boolean,
                        notes:         Option[String]
                      ): Future[Either[BookingError, Enquiry]] =
    for {
      surgeonOpt  <- surgeonRepository.findByUserId(surgeonUserId)
      enquiryOpt  <- bookingRepository.findEnquiryById(enquiryId)
      result <- (surgeonOpt, enquiryOpt) match {
        case (None, _)    => Future.successful(Left(BookingError.SurgeonNotFound))
        case (_, None)    => Future.successful(Left(BookingError.NotFound))
        case (Some(s), Some(e)) if s.id != e.surgeonId =>
          Future.successful(Left(BookingError.Forbidden))
        case (_, Some(e)) if e.status != EnquiryStatus.Pending =>
          Future.successful(Left(BookingError.InvalidStatus))
        case (_, Some(e)) =>
          val newStatus = if (accept) EnquiryStatus.Confirmed else EnquiryStatus.Declined
          bookingRepository.updateEnquiryStatus(e.id, newStatus, notes).flatMap { _ =>
            notify(
              patientRepository.findById(e.patientId).flatMap {
                case Some(p) =>
                  userRepository.findById(p.userId).flatMap {
                    case Some(u) => notificationService.sendEnquiryResponded(u.email, accept)
                    case None    => Future.successful(())
                  }
                case None => Future.successful(())
              }
            ).flatMap { _ =>
              bookingRepository.findEnquiryById(e.id).map {
                case None    => Left(BookingError.NotFound)
                case Some(updated) => Right(updated)
              }
            }
          }
      }
    } yield result

  // ─── Bookings ──────────────────────────────────────────────────────────────

  /** Creates a Pending booking and a matching Stripe PaymentIntent for its fee.
   *  The booking stays Pending — and the parties are not notified — until
   *  confirmBookingPayment() flips it to Confirmed via the payment_intent.succeeded
   *  webhook, so nobody is told a consultation is "confirmed" before it's paid for.
   */
  def createBooking(
                     patientUserId:    UUID,
                     surgeonId:        UUID,
                     enquiryId:        Option[UUID],
                     consultationType: ConsultationType,
                     scheduledAt:      OffsetDateTime,
                     durationMinutes:  Short
                   ): Future[Either[BookingError, BookingWithPayment]] =
    for {
      patientOpt <- patientRepository.findByUserId(patientUserId)
      surgeonOpt <- surgeonRepository.findById(surgeonId)
      result <- (patientOpt, surgeonOpt) match {
        case (None, _)    => Future.successful(Left(BookingError.PatientNotFound))
        case (_, None)    => Future.successful(Left(BookingError.SurgeonNotFound))
        case (Some(patient), Some(surgeon)) =>
          val fee = consultationType match {
            case ConsultationType.InClinic => surgeon.consultFeeClinic.getOrElse(BigDecimal(0))
            case ConsultationType.Virtual  => surgeon.consultFeeVirtual.getOrElse(BigDecimal(0))
          }
          for {
            booking        <- bookingRepository.createBooking(
                                 enquiryId, patient.id, surgeonId,
                                 consultationType, scheduledAt, durationMinutes, fee
                               )
            patientUserOpt <- userRepository.findById(patient.userId)
            intentResult   <- paymentService.createPaymentIntent(booking.id, fee, patientUserOpt.map(_.email))
          } yield Right(BookingWithPayment(booking, intentResult.clientSecret))
      }
    } yield result

  /** Called from the Stripe webhook once payment_intent.succeeded fires:
   *  marks the booking Confirmed + paid, then notifies both parties.
   *
   *  Stripe explicitly guarantees only at-least-once webhook delivery, so the
   *  same event can genuinely arrive twice — guard on the booking's current
   *  status so a redelivery doesn't send a second "confirmed" email.
   */
  def confirmBookingPayment(paymentIntentId: String, bookingId: UUID): Future[Either[BookingError, Booking]] =
    bookingRepository.findBookingById(bookingId).flatMap {
      case None => Future.successful(Left(BookingError.NotFound))
      case Some(existing) if existing.status == BookingStatus.Confirmed =>
        Future.successful(Right(existing))
      case Some(_) =>
        bookingRepository.markBookingPaid(bookingId, paymentIntentId).flatMap { _ =>
          bookingRepository.findBookingById(bookingId).flatMap {
            case None => Future.successful(Left(BookingError.NotFound))
            case Some(b) =>
              notify(
                for {
                  patientOpt     <- patientRepository.findById(b.patientId)
                  surgeonOpt     <- surgeonRepository.findById(b.surgeonId)
                  patientUserOpt <- patientOpt match {
                                       case Some(p) => userRepository.findById(p.userId)
                                       case None    => Future.successful(None)
                                     }
                  surgeonUserOpt <- surgeonOpt match {
                                       case Some(s) => userRepository.findById(s.userId)
                                       case None    => Future.successful(None)
                                     }
                  _ <- (patientUserOpt, surgeonUserOpt) match {
                         case (Some(pu), Some(su)) => notificationService.sendBookingConfirmed(pu.email, su.email)
                         case _                    => Future.successful(())
                       }
                } yield ()
              ).map(_ => Right(b))
          }
        }
    }

  def listBookings(userId: UUID, role: UserRole, status: Option[BookingStatus]): Future[List[Booking]] =
    role match {
      case UserRole.Patient =>
        patientRepository.findByUserId(userId).flatMap {
          case None    => Future.successful(Nil)
          case Some(p) => bookingRepository.listBookingsForPatient(p.id, status)
        }
      case UserRole.Surgeon =>
        surgeonRepository.findByUserId(userId).flatMap {
          case None    => Future.successful(Nil)
          case Some(s) => bookingRepository.listBookingsForSurgeon(s.id, status)
        }
      case _ => Future.successful(Nil)
    }

  def findBooking(id: UUID, userId: UUID, role: UserRole): Future[Either[BookingError, Booking]] =
    bookingRepository.findBookingById(id).flatMap {
      case None => Future.successful(Left(BookingError.NotFound))
      case Some(b) =>
        role match {
          case UserRole.Patient =>
            patientRepository.findByUserId(userId).map {
              case Some(p) if p.id == b.patientId => Right(b)
              case _                               => Left(BookingError.Forbidden)
            }
          case UserRole.Surgeon =>
            surgeonRepository.findByUserId(userId).map {
              case Some(s) if s.id == b.surgeonId => Right(b)
              case _                              => Left(BookingError.Forbidden)
            }
          case UserRole.Admin => Future.successful(Right(b))
        }
    }

  def cancelBooking(
                     bookingId: UUID,
                     userId:    UUID,
                     role:      UserRole,
                     note:      Option[String]
                   ): Future[Either[BookingError, Booking]] =
    findBooking(bookingId, userId, role).flatMap {
      case Left(e)  => Future.successful(Left(e))
      case Right(b) =>
        if (b.status != BookingStatus.Pending && b.status != BookingStatus.Confirmed) {
          Future.successful(Left(BookingError.InvalidStatus))
        } else {
          val cancelStatus = role match {
            case UserRole.Surgeon => BookingStatus.CancelledBySurgeon
            case _                => BookingStatus.CancelledByPatient
          }
          bookingRepository.updateBookingStatus(b.id, cancelStatus, note).flatMap { _ =>
            bookingRepository.findBookingById(b.id).map {
              case None    => Left(BookingError.NotFound)
              case Some(updated) => Right(updated)
            }
          }
        }
    }

  // ─── Reviews ───────────────────────────────────────────────────────────────

  def createReview(
                    patientUserId:       UUID,
                    bookingId:           UUID,
                    rating:              Short,
                    ratingResults:       Option[Short],
                    ratingCommunication: Option[Short],
                    ratingAftercare:     Option[Short],
                    ratingValue:         Option[Short],
                    procedure:           Option[String],
                    body:                String
                  ): Future[Either[BookingError, Review]] =
    for {
      patientOpt <- patientRepository.findByUserId(patientUserId)
      bookingOpt <- bookingRepository.findBookingById(bookingId)
      result <- (patientOpt, bookingOpt) match {
        case (None, _)    => Future.successful(Left(BookingError.PatientNotFound))
        case (_, None)    => Future.successful(Left(BookingError.NotFound))
        case (Some(p), Some(b)) if p.id != b.patientId =>
          Future.successful(Left(BookingError.Forbidden))
        case (_, Some(b)) if b.status != BookingStatus.Completed =>
          Future.successful(Left(BookingError.BookingNotCompleted))
        case (Some(p), Some(b)) =>
          bookingRepository.reviewExistsForBooking(bookingId).flatMap {
            case true  => Future.successful(Left(BookingError.AlreadyReviewed))
            case false =>
              bookingRepository.createReview(
                b.id, p.id, b.surgeonId, rating,
                ratingResults, ratingCommunication, ratingAftercare, ratingValue,
                procedure, body
              ).map(r => Right(r))
          }
      }
    } yield result
}