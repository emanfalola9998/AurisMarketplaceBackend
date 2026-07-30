// ─── app/co/auris/repositories/BookingRepository.scala ───────────────────────

package co.auris.repositories

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.{Bookings, Enquiries, Reviews}
import co.auris.models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BookingRepository @Inject() (
                                    protected val dbConfigProvider: DatabaseConfigProvider
                                  )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  // ─── Enquiries ─────────────────────────────────────────────────────────────

  def createEnquiry(
                     patientId:        UUID,
                     surgeonId:        UUID,
                     procedureInterest: Option[String],
                     goals:            Option[String],
                     previousSurgery:  Boolean,
                     previousDetails:  Option[String],
                     preferredDate:    Option[java.time.LocalDate],
                     preferredTime:    Option[java.time.LocalTime],
                     consultationType: ConsultationType,
                     fee:              BigDecimal,
                     heardAbout:       Option[String]
                   ): Future[Enquiry] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val enquiry = Enquiry(
      id                = UUID.randomUUID(),
      patientId         = patientId,
      surgeonId         = surgeonId,
      procedureInterest = procedureInterest,
      goals             = goals,
      previousSurgery   = previousSurgery,
      previousDetails   = previousDetails,
      preferredDate     = preferredDate,
      preferredTime     = preferredTime,
      consultationType  = consultationType,
      status            = EnquiryStatus.Pending,
      fee               = fee,
      heardAbout        = heardAbout,
      createdAt         = now,
      updatedAt         = now
    )
    db.run((Enquiries += enquiry).map(_ => enquiry))
  }

  def findEnquiryById(id: UUID): Future[Option[Enquiry]] =
    db.run(Enquiries.filter(_.id === id).result.headOption)

  def listEnquiriesForPatient(
                               patientId: UUID,
                               status:    Option[EnquiryStatus] = None
                             ): Future[List[Enquiry]] = {
    val base = Enquiries.filter(_.patientId === patientId)
    val q    = status.fold(base)(s => base.filter(_.status === s))
    db.run(q.sortBy(_.createdAt.desc).result).map(_.toList)
  }

  def listEnquiriesForSurgeon(
                               surgeonId: UUID,
                               status:    Option[EnquiryStatus] = None
                             ): Future[List[Enquiry]] = {
    val base = Enquiries.filter(_.surgeonId === surgeonId)
    val q    = status.fold(base)(s => base.filter(_.status === s))
    db.run(q.sortBy(_.createdAt.desc).result).map(_.toList)
  }

  def updateEnquiryStatus(
                           id:           UUID,
                           status:       EnquiryStatus,
                           surgeonNotes: Option[String] = None
                         ): Future[Int] =
    db.run(
      Enquiries
        .filter(_.id === id)
        .map(e => (e.status, e.surgeonNotes, e.updatedAt))
        .update((status, surgeonNotes, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  /** Counter-proposes a different date/time on a still-pending enquiry —
    * status is left untouched, since this doesn't confirm anything by itself. */
  def updateEnquiryPreferredTime(
                                  id:            UUID,
                                  preferredDate: java.time.LocalDate,
                                  preferredTime: java.time.LocalTime,
                                  surgeonNotes:  Option[String]
                                ): Future[Int] =
    db.run(
      Enquiries
        .filter(_.id === id)
        .map(e => (e.preferredDate, e.preferredTime, e.surgeonNotes, e.updatedAt))
        .update((Some(preferredDate), Some(preferredTime), surgeonNotes, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  def updateEnquiryNotes(id: UUID, surgeonNotes: String): Future[Int] =
    db.run(
      Enquiries
        .filter(_.id === id)
        .map(e => (e.surgeonNotes, e.updatedAt))
        .update((Some(surgeonNotes), OffsetDateTime.now(ZoneOffset.UTC)))
    )

  // ─── Bookings ──────────────────────────────────────────────────────────────

  def createBooking(
                     enquiryId:        Option[UUID],
                     patientId:        UUID,
                     surgeonId:        UUID,
                     consultationType: ConsultationType,
                     scheduledAt:      OffsetDateTime,
                     durationMinutes:  Short,
                     fee:              BigDecimal
                   ): Future[Booking] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val booking = Booking(
      id               = UUID.randomUUID(),
      enquiryId        = enquiryId,
      patientId        = patientId,
      surgeonId        = surgeonId,
      consultationType = consultationType,
      scheduledAt      = scheduledAt,
      durationMinutes  = durationMinutes,
      status           = BookingStatus.Pending,
      fee              = fee,
      createdAt        = now,
      updatedAt        = now
    )
    db.run((Bookings += booking).map(_ => booking))
  }

  def findBookingById(id: UUID): Future[Option[Booking]] =
    db.run(Bookings.filter(_.id === id).result.headOption)

  def listBookingsForPatient(
                              patientId: UUID,
                              status:    Option[BookingStatus] = None
                            ): Future[List[Booking]] = {
    val base = Bookings.filter(_.patientId === patientId)
    val q    = status.fold(base)(s => base.filter(_.status === s))
    db.run(q.sortBy(_.scheduledAt.desc).result).map(_.toList)
  }

  def listBookingsForSurgeon(
                              surgeonId: UUID,
                              status:    Option[BookingStatus] = None
                            ): Future[List[Booking]] = {
    val base = Bookings.filter(_.surgeonId === surgeonId)
    val q    = status.fold(base)(s => base.filter(_.status === s))
    db.run(q.sortBy(_.scheduledAt.desc).result).map(_.toList)
  }

  private val CancelledStatuses: Set[BookingStatus] =
    Set(BookingStatus.CancelledByPatient, BookingStatus.CancelledBySurgeon)

  /** Active (not cancelled) bookings whose scheduled window falls in [from, to).
    * excludeBookingId lets a booking being rescheduled ignore its own current
    * slot when checking what else the surgeon has on. */
  def listActiveForSurgeonInRange(
                                   surgeonId:       UUID,
                                   from:            OffsetDateTime,
                                   to:              OffsetDateTime,
                                   excludeBookingId: Option[UUID] = None
                                 ): Future[List[Booking]] = {
    val base = Bookings.filter { b =>
      b.surgeonId === surgeonId &&
      b.scheduledAt >= from && b.scheduledAt < to &&
      !b.status.inSet(CancelledStatuses)
    }
    val q = excludeBookingId.fold(base)(id => base.filter(_.id =!= id))
    db.run(q.result).map(_.toList)
  }

  def rescheduleBooking(id: UUID, newScheduledAt: OffsetDateTime): Future[Int] =
    db.run(
      Bookings
        .filter(_.id === id)
        .map(b => (b.scheduledAt, b.updatedAt))
        .update((newScheduledAt, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  def updateBookingStatus(
                           id:               UUID,
                           status:           BookingStatus,
                           cancellationNote: Option[String] = None
                         ): Future[Int] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val cancelledAt = status match {
      case BookingStatus.CancelledByPatient | BookingStatus.CancelledBySurgeon => Some(now)
      case _                                                                    => None
    }
    db.run(
      Bookings
        .filter(_.id === id)
        .map(b => (b.status, b.cancelledAt, b.cancellationNote, b.updatedAt))
        .update((status, cancelledAt, cancellationNote, now))
    )
  }

  def markBookingPaid(
                       id:             UUID,
                       stripePaymentId: String
                     ): Future[Int] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    db.run(
      Bookings
        .filter(_.id === id)
        .map(b => (b.status, b.stripePaymentId, b.paidAt, b.updatedAt))
        .update((BookingStatus.Confirmed, Some(stripePaymentId), Some(now), now))
    )
  }

  // ─── Reviews ───────────────────────────────────────────────────────────────

  def createReview(
                    bookingId:           UUID,
                    patientId:           UUID,
                    surgeonId:           UUID,
                    rating:              Short,
                    ratingResults:       Option[Short],
                    ratingCommunication: Option[Short],
                    ratingAftercare:     Option[Short],
                    ratingValue:         Option[Short],
                    procedure:           Option[String],
                    body:                String
                  ): Future[Review] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val review = Review(
      id                  = UUID.randomUUID(),
      bookingId           = bookingId,
      patientId           = patientId,
      surgeonId           = surgeonId,
      rating              = rating,
      ratingResults       = ratingResults,
      ratingCommunication = ratingCommunication,
      ratingAftercare     = ratingAftercare,
      ratingValue         = ratingValue,
      procedure           = procedure,
      body                = body,
      isVerified          = true,
      isPublished         = false,   // admin publishes after moderation
      publishedAt         = None,
      createdAt           = now
    )
    db.run((Reviews += review).map(_ => review))
  }

  def listPublishedReviewsForSurgeon(
                                      surgeonId: UUID,
                                      page:      Int,
                                      pageSize:  Int
                                    ): Future[(List[Review], Long)] = {
    val base   = Reviews.filter(r => r.surgeonId === surgeonId && r.isPublished === true)
    val offset = (page - 1) * pageSize
    for {
      total   <- db.run(base.length.result).map(_.toLong)
      results <- db.run(base.sortBy(_.createdAt.desc).drop(offset).take(pageSize).result)
    } yield (results.toList, total)
  }

  def reviewExistsForBooking(bookingId: UUID): Future[Boolean] =
    db.run(Reviews.filter(_.bookingId === bookingId).exists.result)

  def bookingBelongsToPatient(bookingId: UUID, patientId: UUID): Future[Boolean] =
    db.run(
      Bookings.filter(b => b.id === bookingId && b.patientId === patientId).exists.result
    )
}