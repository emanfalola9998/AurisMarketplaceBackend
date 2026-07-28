// ─── test/co/auris/services/SurgeonServiceSpec.scala ──────────────────────────

package co.auris.services

import co.auris.models._
import co.auris.repositories.SurgeonRepository
import co.auris.support.Fixtures
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SurgeonServiceSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with ArgumentMatchersSugar
  with BeforeAndAfterEach {

  private var surgeonRepository: SurgeonRepository = _
  private var service: SurgeonService = _

  override def beforeEach(): Unit = {
    surgeonRepository = mock[SurgeonRepository]
    service = new SurgeonService(surgeonRepository)
  }

  "findPublicProfile" should {
    "return the profile when it exists and is live" in {
      val surgeon = Fixtures.surgeonProfile(profileLive = true)
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))

      val result = service.findPublicProfile(surgeon.id).futureValue

      result mustBe Right(surgeon)
    }

    "reject a profile that exists but isn't live yet" in {
      val surgeon = Fixtures.surgeonProfile(profileLive = false)
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))

      val result = service.findPublicProfile(surgeon.id).futureValue

      result mustBe Left(SurgeonError.NotLive)
    }

    "return NotFound when no such surgeon exists" in {
      val id = UUID.randomUUID()
      when(surgeonRepository.findById(id)).thenReturn(Future.successful(None))

      val result = service.findPublicProfile(id).futureValue

      result mustBe Left(SurgeonError.NotFound)
    }
  }

  "search" should {
    "map repository results into a Page of SurgeonSearchResult" in {
      val surgeons = List(Fixtures.surgeonProfile(), Fixtures.surgeonProfile())
      when(surgeonRepository.search(Some("Rhinoplasty"), Some("London"), None, 2, 10))
        .thenReturn(Future.successful((surgeons, 25L)))

      val result = service.search(Some("Rhinoplasty"), Some("London"), None, 2, 10).futureValue

      result.items must have size 2
      result.items.map(_.id) must contain theSameElementsAs surgeons.map(_.id)
      result.totalCount mustBe 25L
      result.page mustBe 2
      result.pageSize mustBe 10
    }

    "clamp page numbers below 1 up to 1" in {
      when(surgeonRepository.search(any[Option[String]], any[Option[String]], any[Option[String]], eqTo(1), any[Int]))
        .thenReturn(Future.successful((Nil, 0L)))

      service.search(None, None, None, 0, 20).futureValue

      verify(surgeonRepository).search(None, None, None, 1, 20)
    }

    "clamp page size above 100 down to 100" in {
      when(surgeonRepository.search(any[Option[String]], any[Option[String]], any[Option[String]], any[Int], eqTo(100)))
        .thenReturn(Future.successful((Nil, 0L)))

      service.search(None, None, None, 1, 500).futureValue

      verify(surgeonRepository).search(None, None, None, 1, 100)
    }

    "clamp page size below 1 up to 1" in {
      when(surgeonRepository.search(any[Option[String]], any[Option[String]], any[Option[String]], any[Int], eqTo(1)))
        .thenReturn(Future.successful((Nil, 0L)))

      service.search(None, None, None, 1, 0).futureValue

      verify(surgeonRepository).search(None, None, None, 1, 1)
    }
  }

  "updateProfile" should {
    "return NotFound when the caller has no surgeon profile" in {
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(None))

      val result = service.updateProfile(
        UUID.randomUUID(), "Mr.", "James", "Harrison", "7654321", Nil, None, None, None,
        "Plastic Surgery", Nil, "King's College", "London", None, 10, Nil, None, Nil,
        None, None, true
      ).futureValue

      result mustBe Left(SurgeonError.NotFound)
    }

    "update and return the refreshed profile" in {
      val existing = Fixtures.surgeonProfile()
      val updated  = existing.copy(city = "Manchester")
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(existing)))
      when(surgeonRepository.updateProfile(
        eqTo(existing.id),
        any[String], any[String], any[String], any[String],
        any[List[String]], any[Option[String]], any[Option[Short]], any[Option[String]],
        any[String], any[List[String]], any[String],
        eqTo("Manchester"),
        any[Option[String]], any[Short], any[List[String]], any[Option[String]], any[List[String]],
        any[Option[BigDecimal]], any[Option[BigDecimal]], any[Boolean]
      )).thenReturn(Future.successful(1))
      when(surgeonRepository.findById(existing.id)).thenReturn(Future.successful(Some(updated)))

      val result = service.updateProfile(
        existing.userId, "Mr.", "James", "Harrison", "7654321", Nil, None, None, None,
        "Plastic Surgery", Nil, "King's College", "Manchester", None, 10, Nil, None, Nil,
        None, None, true
      ).futureValue

      result mustBe Right(updated)
    }
  }

  "setProfileLive" should {
    "return NotFound when the caller has no surgeon profile" in {
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(None))

      val result = service.setProfileLive(UUID.randomUUID(), live = true).futureValue

      result mustBe Left(SurgeonError.NotFound)
    }

    "refuse to go live with an incomplete profile" in {
      val surgeon = Fixtures.surgeonProfile(profileComplete = false)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))

      val result = service.setProfileLive(surgeon.userId, live = true).futureValue

      result mustBe Left(SurgeonError.ProfileIncomplete)
      verify(surgeonRepository, never).setProfileLive(any[UUID], any[Boolean])
    }

    "go live when the profile is complete" in {
      val surgeon = Fixtures.surgeonProfile(profileComplete = true)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.setProfileLive(surgeon.id, true)).thenReturn(Future.successful(1))

      val result = service.setProfileLive(surgeon.userId, live = true).futureValue

      result mustBe Right(())
    }

    "allow going offline even with an incomplete profile" in {
      val surgeon = Fixtures.surgeonProfile(profileComplete = false)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.setProfileLive(surgeon.id, false)).thenReturn(Future.successful(1))

      val result = service.setProfileLive(surgeon.userId, live = false).futureValue

      result mustBe Right(())
    }
  }

  "submitApplication" should {
    "return NotFound when the caller has no surgeon profile" in {
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(None))

      val result = service.submitApplication(UUID.randomUUID()).futureValue

      result mustBe Left(SurgeonError.NotFound)
    }

    "refuse to submit an incomplete profile" in {
      val surgeon = Fixtures.surgeonProfile(profileComplete = false)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))

      val result = service.submitApplication(surgeon.userId).futureValue

      result mustBe Left(SurgeonError.ProfileIncomplete)
    }

    "refuse a duplicate application while one is pending" in {
      val surgeon = Fixtures.surgeonProfile(profileComplete = true)
      val pendingApp = Fixtures.surgeonApplication(surgeonId = surgeon.id, status = ApplicationStatus.Pending)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.findApplicationBySurgeonId(surgeon.id)).thenReturn(Future.successful(Some(pendingApp)))

      val result = service.submitApplication(surgeon.userId).futureValue

      result mustBe Left(SurgeonError.AlreadyApplied)
    }

    "refuse a duplicate application while one is in review" in {
      val surgeon = Fixtures.surgeonProfile(profileComplete = true)
      val inReviewApp = Fixtures.surgeonApplication(surgeonId = surgeon.id, status = ApplicationStatus.InReview)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.findApplicationBySurgeonId(surgeon.id)).thenReturn(Future.successful(Some(inReviewApp)))

      val result = service.submitApplication(surgeon.userId).futureValue

      result mustBe Left(SurgeonError.AlreadyApplied)
    }

    "allow reapplying after a previous rejection" in {
      val surgeon = Fixtures.surgeonProfile(profileComplete = true)
      val rejectedApp = Fixtures.surgeonApplication(surgeonId = surgeon.id, status = ApplicationStatus.Rejected)
      val newApp = Fixtures.surgeonApplication(surgeonId = surgeon.id, status = ApplicationStatus.Pending)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.findApplicationBySurgeonId(surgeon.id)).thenReturn(Future.successful(Some(rejectedApp)))
      when(surgeonRepository.submitApplication(surgeon.id)).thenReturn(Future.successful(newApp))

      val result = service.submitApplication(surgeon.userId).futureValue

      result mustBe Right(newApp)
    }

    "submit a fresh application when none exists yet" in {
      val surgeon = Fixtures.surgeonProfile(profileComplete = true)
      val newApp = Fixtures.surgeonApplication(surgeonId = surgeon.id)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(surgeonRepository.findApplicationBySurgeonId(surgeon.id)).thenReturn(Future.successful(None))
      when(surgeonRepository.submitApplication(surgeon.id)).thenReturn(Future.successful(newApp))

      val result = service.submitApplication(surgeon.userId).futureValue

      result mustBe Right(newApp)
    }
  }

  "getDashboard" should {
    "return the profile for an existing surgeon" in {
      val surgeon = Fixtures.surgeonProfile()
      when(surgeonRepository.findByUserId(surgeon.userId)).thenReturn(Future.successful(Some(surgeon)))

      val result = service.getDashboard(surgeon.userId).futureValue

      result mustBe Right(surgeon)
    }

    "return NotFound when there's no profile for the user" in {
      val userId = UUID.randomUUID()
      when(surgeonRepository.findByUserId(userId)).thenReturn(Future.successful(None))

      val result = service.getDashboard(userId).futureValue

      result mustBe Left(SurgeonError.NotFound)
    }
  }
}
