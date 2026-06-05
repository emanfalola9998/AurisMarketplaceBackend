// ─── app/co/auris/repositories/PatientRepository.scala ───────────────────────

package co.auris.repositories

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.{PatientProfiles, SavedSurgeons}
import co.auris.models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PatientRepository @Inject() (
                                    protected val dbConfigProvider: DatabaseConfigProvider
                                  )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  // ─── Profile ───────────────────────────────────────────────────────────────

  def findByUserId(userId: UUID): Future[Option[PatientProfile]] =
    db.run(PatientProfiles.filter(_.userId === userId).result.headOption)

  def findById(id: UUID): Future[Option[PatientProfile]] =
    db.run(PatientProfiles.filter(_.id === id).result.headOption)

  /** Creates a minimal profile row immediately after sign-up. */
  def createProfile(userId: UUID, email: String): Future[PatientProfile] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    // Derive a placeholder name from the email prefix until onboarding completes
    val namePart = email.takeWhile(_ != '@').capitalize
    val profile = PatientProfile(
      id                 = UUID.randomUUID(),
      userId             = userId,
      firstName          = namePart,
      lastName           = "",
      onboardingComplete = false,
      createdAt          = now,
      updatedAt          = now
    )
    db.run((PatientProfiles += profile).map(_ => profile))
  }

  def updateOnboarding(
                        id:                 UUID,
                        firstName:          String,
                        lastName:           String,
                        procedureInterests: List[String],
                        locationPreference: Option[String],
                        consultPreference:  Option[ConsultationType],
                        budgetRange:        Option[String],
                        timeline:           Option[String]
                      ): Future[Int] =
    db.run(
      PatientProfiles
        .filter(_.id === id)
        .map(p => (
          p.firstName, p.lastName, p.procedureInterests,
          p.locationPreference, p.consultPreference,
          p.budgetRange, p.timeline,
          p.onboardingComplete, p.updatedAt
        ))
        .update((
          firstName, lastName, procedureInterests,
          locationPreference, consultPreference,
          budgetRange, timeline,
          true, OffsetDateTime.now(ZoneOffset.UTC)
        ))
    )

  def updateAvatar(id: UUID, avatarUrl: String): Future[Int] =
    db.run(
      PatientProfiles
        .filter(_.id === id)
        .map(p => (p.avatarUrl, p.updatedAt))
        .update((Some(avatarUrl), OffsetDateTime.now(ZoneOffset.UTC)))
    )

  // ─── Saved surgeons ────────────────────────────────────────────────────────

  def savedSurgeonIds(patientId: UUID): Future[List[UUID]] =
    db.run(
      SavedSurgeons.filter(_.patientId === patientId).map(_.surgeonId).result
    ).map(_.toList)

  def saveSurgeon(patientId: UUID, surgeonId: UUID): Future[Unit] = {
    val row = SavedSurgeon(patientId, surgeonId, OffsetDateTime.now(ZoneOffset.UTC))
    db.run(
      SavedSurgeons.insertOrUpdate(row)
    ).map(_ => ())
  }

  def unsaveSurgeon(patientId: UUID, surgeonId: UUID): Future[Unit] =
    db.run(
      SavedSurgeons
        .filter(s => s.patientId === patientId && s.surgeonId === surgeonId)
        .delete
    ).map(_ => ())

  def isSurgeonSaved(patientId: UUID, surgeonId: UUID): Future[Boolean] =
    db.run(
      SavedSurgeons
        .filter(s => s.patientId === patientId && s.surgeonId === surgeonId)
        .exists.result
    )
}


