// ─── app/co/auris/services/SurgeonService.scala ───────────────────────────────

package co.auris.services

import co.auris.models._
import co.auris.repositories.SurgeonRepository

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait SurgeonError
object SurgeonError {
  case object NotFound                  extends SurgeonError
  case object NotLive                   extends SurgeonError
  case object AlreadyApplied            extends SurgeonError
  case object ProfileIncomplete         extends SurgeonError
  case class  Unexpected(msg: String)   extends SurgeonError
}

@Singleton
class SurgeonService @Inject() (
                                 surgeonRepository: SurgeonRepository
                               )(implicit ec: ExecutionContext) {

  // ─── Public profile ──────────────────────────────────────────────────────────

  def findPublicProfile(id: UUID): Future[Either[SurgeonError, SurgeonProfile]] =
    surgeonRepository.findById(id).map {
      case None                         => Left(SurgeonError.NotFound)
      case Some(s) if !s.profileLive   => Left(SurgeonError.NotLive)
      case Some(s)                      => Right(s)
    }

  def search(
              specialty: Option[String],
              city:      Option[String],
              query:     Option[String],
              page:      Int,
              pageSize:  Int
            ): Future[Page[SurgeonSearchResult]] =
    surgeonRepository.search(specialty, city, query, page.max(1), pageSize.min(100).max(1)).map {
      case (surgeons, total) =>
        Page(
          items      = surgeons.map(SurgeonSearchResult.from),
          totalCount = total,
          page       = page,
          pageSize   = pageSize
        )
    }

  // ─── Surgeon onboarding ───────────────────────────────────────────────────────

  def updateProfile(
                     userId:            UUID,
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
                   ): Future[Either[SurgeonError, SurgeonProfile]] =
    surgeonRepository.findByUserId(userId).flatMap {
      case None => Future.successful(Left(SurgeonError.NotFound))
      case Some(existing) =>
        surgeonRepository.updateProfile(
          existing.id, title, firstName, lastName, gmcNumber,
          qualifications, medicalSchool, graduationYear, fellowships,
          specialty, subspecialties, hospital, city, address,
          yearsExperience, languages, bio, procedures,
          consultFeeClinic, consultFeeVirtual, offersVirtual
        ).flatMap { _ =>
          surgeonRepository.findById(existing.id).map {
            case None    => Left(SurgeonError.NotFound)
            case Some(s) => Right(s)
          }
        }
    }

  def setProfileLive(userId: UUID, live: Boolean): Future[Either[SurgeonError, Unit]] =
    surgeonRepository.findByUserId(userId).flatMap {
      case None => Future.successful(Left(SurgeonError.NotFound))
      case Some(s) if live && !s.profileComplete =>
        Future.successful(Left(SurgeonError.ProfileIncomplete))
      case Some(s) =>
        surgeonRepository.setProfileLive(s.id, live).map(_ => Right(()))
    }

  // ─── Application ─────────────────────────────────────────────────────────────

  def submitApplication(userId: UUID): Future[Either[SurgeonError, SurgeonApplication]] =
    surgeonRepository.findByUserId(userId).flatMap {
      case None => Future.successful(Left(SurgeonError.NotFound))
      case Some(profile) if !profile.profileComplete =>
        Future.successful(Left(SurgeonError.ProfileIncomplete))
      case Some(profile) =>
        surgeonRepository.findApplicationBySurgeonId(profile.id).flatMap {
          case Some(existing)
            if existing.status == ApplicationStatus.Pending ||
              existing.status == ApplicationStatus.InReview =>
            Future.successful(Left(SurgeonError.AlreadyApplied))
          case _ =>
            surgeonRepository.submitApplication(profile.id).map(app => Right(app))
        }
    }

  def getDashboard(userId: UUID): Future[Either[SurgeonError, SurgeonProfile]] =
    surgeonRepository.findByUserId(userId).map {
      case None    => Left(SurgeonError.NotFound)
      case Some(s) => Right(s)
    }
}


