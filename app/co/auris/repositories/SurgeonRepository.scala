// ─── app/co/auris/repositories/SurgeonRepository.scala ───────────────────────

package co.auris.repositories

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.{SurgeonApplications, SurgeonProfiles}
import co.auris.models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SurgeonRepository @Inject() (
                                    protected val dbConfigProvider: DatabaseConfigProvider
                                  )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  // ─── Profile ───────────────────────────────────────────────────────────────

  def findByUserId(userId: UUID): Future[Option[SurgeonProfile]] =
    db.run(SurgeonProfiles.filter(_.userId === userId).result.headOption)

  def findById(id: UUID): Future[Option[SurgeonProfile]] =
    db.run(SurgeonProfiles.filter(_.id === id).result.headOption)

  /** Creates a minimal profile row immediately after sign-up. Filled out during onboarding. */
  def createProfile(userId: UUID, email: String): Future[SurgeonProfile] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val profile = SurgeonProfile(
      id          = UUID.randomUUID(),
      userId      = userId,
      firstName   = "",
      lastName    = "",
      gmcNumber   = s"PENDING-${UUID.randomUUID()}",
      specialty   = "",
      hospital    = "",
      city        = "",
      createdAt   = now,
      updatedAt   = now
    )
    db.run((SurgeonProfiles += profile).map(_ => profile))
  }

  def updateProfile(
                     id:                UUID,
                     title:             String,
                     firstName:         String,
                     lastName:          String,
                     gmcNumber:         String,
                     qualifications:    List[String],
                     medicalSchool:     Option[String],
                     graduationYear:    Option[Short],
                     fellowships:       Option[String],
                     specialty:         String,
                     subspecialties:    List[String],
                     hospital:          String,
                     city:              String,
                     address:           Option[String],
                     yearsExperience:   Short,
                     languages:         List[String],
                     bio:               Option[String],
                     procedures:        List[String],
                     consultFeeClinic:  Option[BigDecimal],
                     consultFeeVirtual: Option[BigDecimal],
                     offersVirtual:     Boolean
                   ): Future[Int] =
    db.run(
      SurgeonProfiles
        .filter(_.id === id)
        .map(s => (
          s.title, s.firstName, s.lastName, s.gmcNumber,
          s.qualifications, s.medicalSchool, s.graduationYear, s.fellowships,
          s.specialty, s.subspecialties, s.hospital, s.city, s.address,
          s.yearsExperience, s.languages, s.bio, s.procedures,
          s.consultFeeClinic, s.consultFeeVirtual, s.offersVirtual,
          s.profileComplete, s.updatedAt
        ))
        .update((
          title, firstName, lastName, gmcNumber,
          qualifications, medicalSchool, graduationYear, fellowships,
          specialty, subspecialties, hospital, city, address,
          yearsExperience, languages, bio, procedures,
          consultFeeClinic, consultFeeVirtual, offersVirtual,
          true, OffsetDateTime.now(ZoneOffset.UTC)
        ))
    )

  def setProfileLive(id: UUID, live: Boolean): Future[Int] =
    db.run(
      SurgeonProfiles
        .filter(_.id === id)
        .map(s => (s.profileLive, s.updatedAt))
        .update((live, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  def updateAvatar(id: UUID, avatarUrl: String): Future[Int] =
    db.run(
      SurgeonProfiles
        .filter(_.id === id)
        .map(s => (s.avatarUrl, s.updatedAt))
        .update((Some(avatarUrl), OffsetDateTime.now(ZoneOffset.UTC)))
    )

  // ─── Billing ───────────────────────────────────────────────────────────────

  def findByStripeCustomerId(customerId: String): Future[Option[SurgeonProfile]] =
    db.run(SurgeonProfiles.filter(_.stripeCustomerId === customerId).result.headOption)

  def setStripeCustomerId(id: UUID, customerId: String): Future[Int] =
    db.run(
      SurgeonProfiles
        .filter(_.id === id)
        .map(s => (s.stripeCustomerId, s.updatedAt))
        .update((Some(customerId), OffsetDateTime.now(ZoneOffset.UTC)))
    )

  def setSubscriptionStatus(id: UUID, status: SubscriptionStatus, renewsAt: Option[OffsetDateTime]): Future[Int] =
    db.run(
      SurgeonProfiles
        .filter(_.id === id)
        .map(s => (s.subscriptionStatus, s.subscriptionRenewsAt, s.updatedAt))
        .update((status, renewsAt, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  // ─── Search ────────────────────────────────────────────────────────────────

  def search(
              specialty: Option[String],
              city:      Option[String],
              query:     Option[String],
              page:      Int,
              pageSize:  Int
            ): Future[(List[SurgeonProfile], Long)] = {
    val base = SurgeonProfiles.filter(_.profileLive === true)

    val filtered = (specialty, city, query) match {
      case (Some(s), Some(c), Some(q)) =>
        base.filter(r => r.specialty === s && r.city === c &&
          (r.firstName ++ " " ++ r.lastName).toLowerCase.like(s"%${q.toLowerCase}%"))
      case (Some(s), Some(c), None) =>
        base.filter(r => r.specialty === s && r.city === c)
      case (Some(s), None, Some(q)) =>
        base.filter(r => r.specialty === s &&
          (r.firstName ++ " " ++ r.lastName).toLowerCase.like(s"%${q.toLowerCase}%"))
      case (None, Some(c), Some(q)) =>
        base.filter(r => r.city === c &&
          (r.firstName ++ " " ++ r.lastName).toLowerCase.like(s"%${q.toLowerCase}%"))
      case (Some(s), None, None) => base.filter(_.specialty === s)
      case (None, Some(c), None) => base.filter(_.city === c)
      case (None, None, Some(q)) =>
        base.filter(r =>
          (r.firstName ++ " " ++ r.lastName).toLowerCase.like(s"%${q.toLowerCase}%"))
      case _ => base
    }

    val sorted = filtered.sortBy(r => (r.tier, r.rating.desc))
    val offset = (page - 1) * pageSize

    for {
      total   <- db.run(filtered.length.result).map(_.toLong)
      results <- db.run(sorted.drop(offset).take(pageSize).result)
    } yield (results.toList, total)
  }

  // ─── Applications ──────────────────────────────────────────────────────────

  def submitApplication(surgeonId: UUID): Future[SurgeonApplication] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val app = SurgeonApplication(
      id          = UUID.randomUUID(),
      surgeonId   = surgeonId,
      status      = ApplicationStatus.Pending,
      submittedAt = now
    )
    db.run((SurgeonApplications += app).map(_ => app))
  }

  def findApplicationById(id: UUID): Future[Option[SurgeonApplication]] =
    db.run(SurgeonApplications.filter(_.id === id).result.headOption)

  def findApplicationBySurgeonId(surgeonId: UUID): Future[Option[SurgeonApplication]] =
    db.run(
      SurgeonApplications
        .filter(_.surgeonId === surgeonId)
        .sortBy(_.submittedAt.desc)
        .result.headOption
    )

  def listApplicationsByStatus(status: ApplicationStatus): Future[List[SurgeonApplication]] =
    db.run(
      SurgeonApplications
        .filter(_.status === status)
        .sortBy(_.submittedAt.desc)
        .result
    ).map(_.toList)

  def updateApplicationStatus(
                               id:            UUID,
                               status:        ApplicationStatus,
                               reviewerId:    Option[UUID],
                               reviewerNotes: Option[String],
                               flags:         List[String],
                               score:         Option[Short]
                             ): Future[Int] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    db.run(
      SurgeonApplications
        .filter(_.id === id)
        .map(a => (a.status, a.reviewerId, a.reviewerNotes, a.flags, a.score, a.reviewedAt))
        .update((status, reviewerId, reviewerNotes, flags, score, Some(now)))
    )
  }

  def approveApplication(id: UUID, reviewerId: UUID): Future[Int] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    db.run(
      SurgeonApplications
        .filter(_.id === id)
        .map(a => (a.status, a.reviewerId, a.reviewedAt, a.approvedAt))
        .update((ApplicationStatus.Approved, Some(reviewerId), Some(now), Some(now)))
    )
  }
}