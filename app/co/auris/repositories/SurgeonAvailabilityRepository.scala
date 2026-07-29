// ─── app/co/auris/repositories/SurgeonAvailabilityRepository.scala ───────────

package co.auris.repositories

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.{SurgeonAvailabilities, SurgeonBlockedSlots}
import co.auris.models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SurgeonAvailabilityRepository @Inject() (
                                                protected val dbConfigProvider: DatabaseConfigProvider
                                              )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  def listForSurgeon(surgeonId: UUID): Future[List[SurgeonAvailability]] =
    db.run(
      SurgeonAvailabilities
        .filter(_.surgeonId === surgeonId)
        .sortBy(a => (a.dayOfWeek, a.startTime))
        .result
    ).map(_.toList)

  def listBlockedForSurgeonInRange(
                                    surgeonId: UUID,
                                    from:      OffsetDateTime,
                                    to:        OffsetDateTime
                                  ): Future[List[SurgeonBlockedSlot]] =
    db.run(
      SurgeonBlockedSlots
        .filter(b => b.surgeonId === surgeonId && b.blockedAt >= from && b.blockedAt < to)
        .result
    ).map(_.toList)

  /** Replaces the surgeon's entire weekly schedule with the given slots. */
  def replaceForSurgeon(
                         surgeonId: UUID,
                         slots:     List[NewAvailabilitySlot]
                       ): Future[List[SurgeonAvailability]] = {
    val rows = slots.map { s =>
      SurgeonAvailability(
        id            = UUID.randomUUID(),
        surgeonId     = surgeonId,
        dayOfWeek     = s.dayOfWeek,
        startTime     = s.startTime,
        endTime       = s.endTime,
        bufferMinutes = s.bufferMinutes,
        isActive      = s.isActive
      )
    }

    val action = for {
      _ <- SurgeonAvailabilities.filter(_.surgeonId === surgeonId).delete
      _ <- SurgeonAvailabilities ++= rows
    } yield rows

    db.run(action.transactionally)
  }
}

case class NewAvailabilitySlot(
                                dayOfWeek:     Short,
                                startTime:     java.time.LocalTime,
                                endTime:       java.time.LocalTime,
                                bufferMinutes: Short   = 30,
                                isActive:      Boolean = true
                              )
