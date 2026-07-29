// ─── app/co/auris/services/AvailabilityService.scala ──────────────────────────
//
// Turns a surgeon's weekly availability template into concrete bookable slots
// for a date range, excluding anything already booked or explicitly blocked.

package co.auris.services

import co.auris.models._
import co.auris.repositories.{BookingRepository, SurgeonAvailabilityRepository}

import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AvailabilityService @Inject() (
                                      availabilityRepository: SurgeonAvailabilityRepository,
                                      bookingRepository:      BookingRepository
                                    )(implicit ec: ExecutionContext) {

  private val SlotDurationMinutes = 60

  def availableSlots(surgeonId: UUID, from: LocalDate, to: LocalDate): Future[List[AvailableDay]] = {
    val rangeStart = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime
    val rangeEnd   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime

    for {
      template <- availabilityRepository.listForSurgeon(surgeonId)
      blocked  <- availabilityRepository.listBlockedForSurgeonInRange(surgeonId, rangeStart, rangeEnd)
      booked   <- bookingRepository.listActiveForSurgeonInRange(surgeonId, rangeStart, rangeEnd)
    } yield {
      val occupied: List[(OffsetDateTime, OffsetDateTime)] =
        blocked.map(b => (b.blockedAt, b.blockedAt.plusMinutes(b.durationMins.toLong))) ++
        booked.map(b => (b.scheduledAt, b.scheduledAt.plusMinutes(b.durationMinutes.toLong)))

      val now             = OffsetDateTime.now(ZoneOffset.UTC)
      val activeTemplate  = template.filter(_.isActive)

      Iterator.iterate(from)(_.plusDays(1))
        .takeWhile(!_.isAfter(to))
        .map { date =>
          val isoDayOfWeek = date.getDayOfWeek.getValue.toShort
          val slots = activeTemplate
            .filter(_.dayOfWeek == isoDayOfWeek)
            .flatMap(window => candidateSlots(date, window.startTime, window.endTime, window.bufferMinutes))
            .filter { slotStart =>
              val slotEnd = slotStart.plusMinutes(SlotDurationMinutes.toLong)
              slotStart.isAfter(now) && occupied.forall { case (occStart, occEnd) =>
                !(slotStart.isBefore(occEnd) && occStart.isBefore(slotEnd))
              }
            }
            .map(_.toLocalTime)
            .sorted

          AvailableDay(date, slots)
        }
        .toList
    }
  }

  /** Slot start times within [windowStart, windowEnd), spaced by duration + buffer. Uses
    * minutes-since-midnight integer arithmetic so it can never wrap past midnight. */
  private def candidateSlots(
                              date:          LocalDate,
                              windowStart:   LocalTime,
                              windowEnd:     LocalTime,
                              bufferMinutes: Short
                            ): List[OffsetDateTime] = {
    val startMin = windowStart.toSecondOfDay / 60
    val endMin   = windowEnd.toSecondOfDay / 60
    val step     = SlotDurationMinutes + bufferMinutes

    Iterator.iterate(startMin)(_ + step)
      .takeWhile(m => m + SlotDurationMinutes <= endMin)
      .map(m => OffsetDateTime.of(date, LocalTime.ofSecondOfDay(m * 60L), ZoneOffset.UTC))
      .toList
  }
}
