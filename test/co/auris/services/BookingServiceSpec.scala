// ─── test/co/auris/services/BookingServiceSpec.scala ──────────────────────────

package co.auris.services

import co.auris.models._
import co.auris.repositories.{BookingRepository, PatientRepository, SurgeonRepository, UserRepository}
import co.auris.support.Fixtures
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{LocalDate, LocalTime, OffsetDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BookingServiceSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with ArgumentMatchersSugar
  with BeforeAndAfterEach {

  private var bookingRepository: BookingRepository = _
  private var patientRepository: PatientRepository = _
  private var surgeonRepository: SurgeonRepository = _
  private var userRepository:    UserRepository    = _
  private var notificationService: NotificationService = _
  private var paymentService:    PaymentService    = _
  private var service: BookingService = _

  override def beforeEach(): Unit = {
    bookingRepository   = mock[BookingRepository]
    patientRepository   = mock[PatientRepository]
    surgeonRepository   = mock[SurgeonRepository]
    userRepository       = mock[UserRepository]
    notificationService = mock[NotificationService]
    paymentService       = mock[PaymentService]
    service = new BookingService(bookingRepository, patientRepository, surgeonRepository, userRepository, notificationService, paymentService)

    // Notification sends are best-effort throughout — default them to succeed
    // so tests that don't care about notifications don't need to stub them.
    when(notificationService.sendEnquiryReceived(any[String])).thenReturn(Future.successful(()))
    when(notificationService.sendEnquiryResponded(any[String], any[Boolean])).thenReturn(Future.successful(()))
    when(notificationService.sendBookingConfirmed(any[String], any[String])).thenReturn(Future.successful(()))
    when(paymentService.createPaymentIntent(any[UUID], any[BigDecimal], any[Option[String]]))
      .thenReturn(Future.successful(PaymentIntentResult("pi_test_123", "pi_test_123_secret_abc")))
    ()
  }

  "createEnquiry" should {
    "fail with PatientNotFound when the caller has no patient profile" in {
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(None))
      when(surgeonRepository.findById(any[UUID])).thenReturn(Future.successful(Some(Fixtures.surgeonProfile())))

      val result = service.createEnquiry(
        UUID.randomUUID(), UUID.randomUUID(), None, None, false, None, None, None,
        ConsultationType.InClinic, None
      ).futureValue

      result mustBe Left(BookingError.PatientNotFound)
    }

    "fail with SurgeonNotFound when the surgeon doesn't exist" in {
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(Fixtures.patientProfile())))
      when(surgeonRepository.findById(any[UUID])).thenReturn(Future.successful(None))

      val result = service.createEnquiry(
        UUID.randomUUID(), UUID.randomUUID(), None, None, false, None, None, None,
        ConsultationType.InClinic, None
      ).futureValue

      result mustBe Left(BookingError.SurgeonNotFound)
    }

    "charge the in-clinic fee for an in-clinic enquiry" in {
      val patient = Fixtures.patientProfile()
      val surgeon = Fixtures.surgeonProfile(consultFeeClinic = Some(BigDecimal(400)), consultFeeVirtual = Some(BigDecimal(150)))
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(surgeon.userId)).thenReturn(Future.successful(Some(Fixtures.user())))
      when(bookingRepository.createEnquiry(
        eqTo(patient.id), eqTo(surgeon.id),
        any[Option[String]], any[Option[String]], any[Boolean], any[Option[String]],
        any[Option[LocalDate]], any[Option[LocalTime]],
        eqTo(ConsultationType.InClinic), eqTo(BigDecimal(400)), any[Option[String]]
      )).thenReturn(Future.successful(Fixtures.enquiry(patientId = patient.id, surgeonId = surgeon.id, fee = BigDecimal(400))))

      val result = service.createEnquiry(
        UUID.randomUUID(), surgeon.id, None, None, false, None, None, None,
        ConsultationType.InClinic, None
      ).futureValue

      result mustBe a[Right[_, _]]
      result.toOption.get.fee mustBe BigDecimal(400)
    }

    "charge the virtual fee for a virtual enquiry" in {
      val patient = Fixtures.patientProfile()
      val surgeon = Fixtures.surgeonProfile(consultFeeClinic = Some(BigDecimal(400)), consultFeeVirtual = Some(BigDecimal(150)))
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(surgeon.userId)).thenReturn(Future.successful(Some(Fixtures.user())))
      when(bookingRepository.createEnquiry(
        eqTo(patient.id), eqTo(surgeon.id),
        any[Option[String]], any[Option[String]], any[Boolean], any[Option[String]],
        any[Option[LocalDate]], any[Option[LocalTime]],
        eqTo(ConsultationType.Virtual), eqTo(BigDecimal(150)), any[Option[String]]
      )).thenReturn(Future.successful(Fixtures.enquiry(patientId = patient.id, surgeonId = surgeon.id, fee = BigDecimal(150))))

      val result = service.createEnquiry(
        UUID.randomUUID(), surgeon.id, None, None, false, None, None, None,
        ConsultationType.Virtual, None
      ).futureValue

      result mustBe a[Right[_, _]]
      result.toOption.get.fee mustBe BigDecimal(150)
    }

    "still succeed even if notifying the surgeon fails" in {
      val patient = Fixtures.patientProfile()
      val surgeon = Fixtures.surgeonProfile()
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(surgeon.userId)).thenReturn(Future.successful(Some(Fixtures.user())))
      when(bookingRepository.createEnquiry(
        any[UUID], any[UUID], any[Option[String]], any[Option[String]], any[Boolean], any[Option[String]],
        any[Option[LocalDate]], any[Option[LocalTime]], any[ConsultationType], any[BigDecimal], any[Option[String]]
      )).thenReturn(Future.successful(Fixtures.enquiry(patientId = patient.id, surgeonId = surgeon.id)))
      when(notificationService.sendEnquiryReceived(any[String])).thenReturn(Future.failed(new RuntimeException("SMTP down")))

      val result = service.createEnquiry(
        UUID.randomUUID(), surgeon.id, None, None, false, None, None, None,
        ConsultationType.InClinic, None
      ).futureValue

      result mustBe a[Right[_, _]]
    }
  }

  "listEnquiries" should {
    "list a patient's own enquiries" in {
      val patient = Fixtures.patientProfile()
      val userId = UUID.randomUUID()
      when(patientRepository.findByUserId(userId)).thenReturn(Future.successful(Some(patient)))
      when(bookingRepository.listEnquiriesForPatient(patient.id, None)).thenReturn(Future.successful(List(Fixtures.enquiry())))

      val result = service.listEnquiries(userId, UserRole.Patient, None).futureValue

      result must have size 1
    }

    "return nothing for a patient with no profile" in {
      val userId = UUID.randomUUID()
      when(patientRepository.findByUserId(userId)).thenReturn(Future.successful(None))

      val result = service.listEnquiries(userId, UserRole.Patient, None).futureValue

      result mustBe Nil
    }

    "list a surgeon's own enquiries" in {
      val surgeon = Fixtures.surgeonProfile()
      val userId = UUID.randomUUID()
      when(surgeonRepository.findByUserId(userId)).thenReturn(Future.successful(Some(surgeon)))
      when(bookingRepository.listEnquiriesForSurgeon(surgeon.id, None)).thenReturn(Future.successful(List(Fixtures.enquiry(), Fixtures.enquiry())))

      val result = service.listEnquiries(userId, UserRole.Surgeon, None).futureValue

      result must have size 2
    }

    "return nothing for an admin (no enquiry ownership concept)" in {
      val result = service.listEnquiries(UUID.randomUUID(), UserRole.Admin, None).futureValue

      result mustBe Nil
    }
  }

  "respondToEnquiry" should {
    "fail with SurgeonNotFound when the caller has no surgeon profile" in {
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(None))
      when(bookingRepository.findEnquiryById(any[UUID])).thenReturn(Future.successful(Some(Fixtures.enquiry())))

      val result = service.respondToEnquiry(UUID.randomUUID(), UUID.randomUUID(), accept = true, None).futureValue

      result mustBe Left(BookingError.SurgeonNotFound)
    }

    "fail with NotFound when the enquiry doesn't exist" in {
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(Fixtures.surgeonProfile())))
      when(bookingRepository.findEnquiryById(any[UUID])).thenReturn(Future.successful(None))

      val result = service.respondToEnquiry(UUID.randomUUID(), UUID.randomUUID(), accept = true, None).futureValue

      result mustBe Left(BookingError.NotFound)
    }

    "fail with Forbidden when the enquiry belongs to a different surgeon" in {
      val surgeon = Fixtures.surgeonProfile()
      val enquiry = Fixtures.enquiry(surgeonId = UUID.randomUUID())
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(bookingRepository.findEnquiryById(enquiry.id)).thenReturn(Future.successful(Some(enquiry)))

      val result = service.respondToEnquiry(UUID.randomUUID(), enquiry.id, accept = true, None).futureValue

      result mustBe Left(BookingError.Forbidden)
    }

    "fail with InvalidStatus when the enquiry is no longer pending" in {
      val surgeon = Fixtures.surgeonProfile()
      val enquiry = Fixtures.enquiry(surgeonId = surgeon.id, status = EnquiryStatus.Confirmed)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(bookingRepository.findEnquiryById(enquiry.id)).thenReturn(Future.successful(Some(enquiry)))

      val result = service.respondToEnquiry(UUID.randomUUID(), enquiry.id, accept = true, None).futureValue

      result mustBe Left(BookingError.InvalidStatus)
    }

    "confirm the enquiry on accept and notify the patient" in {
      val surgeon = Fixtures.surgeonProfile()
      val patient = Fixtures.patientProfile()
      val enquiry = Fixtures.enquiry(surgeonId = surgeon.id, patientId = patient.id, status = EnquiryStatus.Pending)
      val updated = enquiry.copy(status = EnquiryStatus.Confirmed)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(bookingRepository.findEnquiryById(enquiry.id)).thenReturn(Future.successful(Some(enquiry)), Future.successful(Some(updated)))
      when(bookingRepository.updateEnquiryStatus(enquiry.id, EnquiryStatus.Confirmed, None)).thenReturn(Future.successful(1))
      when(patientRepository.findById(patient.id)).thenReturn(Future.successful(Some(patient)))
      when(userRepository.findById(patient.userId)).thenReturn(Future.successful(Some(Fixtures.user())))

      val result = service.respondToEnquiry(UUID.randomUUID(), enquiry.id, accept = true, None).futureValue

      result mustBe Right(updated)
      verify(notificationService).sendEnquiryResponded(any[String], eqTo(true))
    }

    "decline the enquiry when accept = false" in {
      val surgeon = Fixtures.surgeonProfile()
      val enquiry = Fixtures.enquiry(surgeonId = surgeon.id, status = EnquiryStatus.Pending)
      val updated = enquiry.copy(status = EnquiryStatus.Declined)
      when(surgeonRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(surgeon)))
      when(bookingRepository.findEnquiryById(enquiry.id)).thenReturn(Future.successful(Some(enquiry)), Future.successful(Some(updated)))
      when(bookingRepository.updateEnquiryStatus(enquiry.id, EnquiryStatus.Declined, None)).thenReturn(Future.successful(1))
      when(patientRepository.findById(enquiry.patientId)).thenReturn(Future.successful(None))

      val result = service.respondToEnquiry(UUID.randomUUID(), enquiry.id, accept = false, None).futureValue

      result mustBe Right(updated)
      verify(bookingRepository).updateEnquiryStatus(enquiry.id, EnquiryStatus.Declined, None)
    }
  }

  "createBooking" should {
    "fail with PatientNotFound when the caller has no patient profile" in {
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(None))
      when(surgeonRepository.findById(any[UUID])).thenReturn(Future.successful(Some(Fixtures.surgeonProfile())))

      val result = service.createBooking(
        UUID.randomUUID(), UUID.randomUUID(), None, ConsultationType.InClinic, OffsetDateTime.now(), 60
      ).futureValue

      result mustBe Left(BookingError.PatientNotFound)
    }

    "fail with SurgeonNotFound when the surgeon doesn't exist" in {
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(Fixtures.patientProfile())))
      when(surgeonRepository.findById(any[UUID])).thenReturn(Future.successful(None))

      val result = service.createBooking(
        UUID.randomUUID(), UUID.randomUUID(), None, ConsultationType.InClinic, OffsetDateTime.now(), 60
      ).futureValue

      result mustBe Left(BookingError.SurgeonNotFound)
    }

    "create a Pending booking, open a Stripe PaymentIntent for its fee, and return the client secret" in {
      val patient = Fixtures.patientProfile()
      val surgeon = Fixtures.surgeonProfile()
      val scheduledAt = OffsetDateTime.now().plusDays(14)
      val patientUser = Fixtures.user()
      val createdBooking = Fixtures.booking(patientId = patient.id, surgeonId = surgeon.id)
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(patient.userId)).thenReturn(Future.successful(Some(patientUser)))
      when(bookingRepository.createBooking(
        any[Option[UUID]], eqTo(patient.id), eqTo(surgeon.id), eqTo(ConsultationType.InClinic), eqTo(scheduledAt), eqTo(60.toShort), any[BigDecimal]
      )).thenReturn(Future.successful(createdBooking))
      when(paymentService.createPaymentIntent(eqTo(createdBooking.id), any[BigDecimal], eqTo(Some(patientUser.email))))
        .thenReturn(Future.successful(PaymentIntentResult("pi_abc", "pi_abc_secret_xyz")))

      val result = service.createBooking(
        UUID.randomUUID(), surgeon.id, None, ConsultationType.InClinic, scheduledAt, 60
      ).futureValue

      result mustBe Right(BookingWithPayment(createdBooking, "pi_abc_secret_xyz"))
      verify(paymentService).createPaymentIntent(eqTo(createdBooking.id), any[BigDecimal], eqTo(Some(patientUser.email)))
    }

    "notifies no one yet — the booking isn't paid for" in {
      val patient = Fixtures.patientProfile()
      val surgeon = Fixtures.surgeonProfile()
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(patient.userId)).thenReturn(Future.successful(Some(Fixtures.user())))
      when(bookingRepository.createBooking(
        any[Option[UUID]], any[UUID], any[UUID], any[ConsultationType], any[OffsetDateTime], any[Short], any[BigDecimal]
      )).thenReturn(Future.successful(Fixtures.booking(patientId = patient.id, surgeonId = surgeon.id)))

      service.createBooking(
        UUID.randomUUID(), surgeon.id, None, ConsultationType.InClinic, OffsetDateTime.now(), 60
      ).futureValue

      verify(notificationService, never).sendBookingConfirmed(any[String], any[String])
    }
  }

  "confirmBookingPayment" should {
    "mark the booking paid and notify both parties" in {
      val patient = Fixtures.patientProfile()
      val surgeon = Fixtures.surgeonProfile()
      val pending = Fixtures.booking(patientId = patient.id, surgeonId = surgeon.id, status = BookingStatus.Pending)
      val confirmed = Fixtures.booking(id = pending.id, patientId = patient.id, surgeonId = surgeon.id, status = BookingStatus.Confirmed)
      when(bookingRepository.markBookingPaid(pending.id, "pi_abc")).thenReturn(Future.successful(1))
      when(bookingRepository.findBookingById(pending.id)).thenReturn(Future.successful(Some(pending)), Future.successful(Some(confirmed)))
      when(patientRepository.findById(patient.id)).thenReturn(Future.successful(Some(patient)))
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(patient.userId)).thenReturn(Future.successful(Some(Fixtures.user())))
      when(userRepository.findById(surgeon.userId)).thenReturn(Future.successful(Some(Fixtures.user())))

      val result = service.confirmBookingPayment("pi_abc", pending.id).futureValue

      result mustBe Right(confirmed)
      verify(bookingRepository).markBookingPaid(pending.id, "pi_abc")
      verify(notificationService).sendBookingConfirmed(any[String], any[String])
    }

    "does nothing and skips re-notifying when the booking is already Confirmed (idempotent webhook redelivery)" in {
      val booking = Fixtures.booking(status = BookingStatus.Confirmed)
      when(bookingRepository.findBookingById(booking.id)).thenReturn(Future.successful(Some(booking)))

      val result = service.confirmBookingPayment("pi_abc", booking.id).futureValue

      result mustBe Right(booking)
      verify(bookingRepository, never).markBookingPaid(any[UUID], any[String])
      verify(notificationService, never).sendBookingConfirmed(any[String], any[String])
    }

    "fail with NotFound when the booking doesn't exist at all" in {
      val bookingId = UUID.randomUUID()
      when(bookingRepository.findBookingById(bookingId)).thenReturn(Future.successful(None))

      val result = service.confirmBookingPayment("pi_abc", bookingId).futureValue

      result mustBe Left(BookingError.NotFound)
      verify(bookingRepository, never).markBookingPaid(any[UUID], any[String])
    }

    "fail with NotFound if the booking has vanished after being marked paid" in {
      val bookingId = UUID.randomUUID()
      val pending = Fixtures.booking(id = bookingId, status = BookingStatus.Pending)
      when(bookingRepository.markBookingPaid(bookingId, "pi_abc")).thenReturn(Future.successful(1))
      when(bookingRepository.findBookingById(bookingId)).thenReturn(Future.successful(Some(pending)), Future.successful(None))

      val result = service.confirmBookingPayment("pi_abc", bookingId).futureValue

      result mustBe Left(BookingError.NotFound)
    }

    "still succeed even if notifying the parties fails" in {
      val patient = Fixtures.patientProfile()
      val surgeon = Fixtures.surgeonProfile()
      val pending = Fixtures.booking(patientId = patient.id, surgeonId = surgeon.id, status = BookingStatus.Pending)
      when(bookingRepository.markBookingPaid(pending.id, "pi_abc")).thenReturn(Future.successful(1))
      when(bookingRepository.findBookingById(pending.id)).thenReturn(Future.successful(Some(pending)))
      when(patientRepository.findById(patient.id)).thenReturn(Future.successful(Some(patient)))
      when(surgeonRepository.findById(surgeon.id)).thenReturn(Future.successful(Some(surgeon)))
      when(userRepository.findById(patient.userId)).thenReturn(Future.successful(Some(Fixtures.user())))
      when(userRepository.findById(surgeon.userId)).thenReturn(Future.successful(Some(Fixtures.user())))
      when(notificationService.sendBookingConfirmed(any[String], any[String])).thenReturn(Future.failed(new RuntimeException("SMTP down")))

      val result = service.confirmBookingPayment("pi_abc", pending.id).futureValue

      result mustBe Right(pending)
    }
  }

  "findBooking" should {
    "fail with NotFound when the booking doesn't exist" in {
      when(bookingRepository.findBookingById(any[UUID])).thenReturn(Future.successful(None))

      val result = service.findBooking(UUID.randomUUID(), UUID.randomUUID(), UserRole.Patient).futureValue

      result mustBe Left(BookingError.NotFound)
    }

    "allow the owning patient to view their booking" in {
      val patient = Fixtures.patientProfile()
      val userId = UUID.randomUUID()
      val b = Fixtures.booking(patientId = patient.id)
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))
      when(patientRepository.findByUserId(userId)).thenReturn(Future.successful(Some(patient)))

      val result = service.findBooking(b.id, userId, UserRole.Patient).futureValue

      result mustBe Right(b)
    }

    "forbid a patient from viewing someone else's booking" in {
      val b = Fixtures.booking(patientId = UUID.randomUUID())
      val userId = UUID.randomUUID()
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))
      when(patientRepository.findByUserId(userId)).thenReturn(Future.successful(Some(Fixtures.patientProfile())))

      val result = service.findBooking(b.id, userId, UserRole.Patient).futureValue

      result mustBe Left(BookingError.Forbidden)
    }

    "allow the owning surgeon to view their booking" in {
      val surgeon = Fixtures.surgeonProfile()
      val userId = UUID.randomUUID()
      val b = Fixtures.booking(surgeonId = surgeon.id)
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))
      when(surgeonRepository.findByUserId(userId)).thenReturn(Future.successful(Some(surgeon)))

      val result = service.findBooking(b.id, userId, UserRole.Surgeon).futureValue

      result mustBe Right(b)
    }

    "forbid a surgeon from viewing someone else's booking" in {
      val b = Fixtures.booking(surgeonId = UUID.randomUUID())
      val userId = UUID.randomUUID()
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))
      when(surgeonRepository.findByUserId(userId)).thenReturn(Future.successful(Some(Fixtures.surgeonProfile())))

      val result = service.findBooking(b.id, userId, UserRole.Surgeon).futureValue

      result mustBe Left(BookingError.Forbidden)
    }

    "let an admin view any booking" in {
      val b = Fixtures.booking()
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))

      val result = service.findBooking(b.id, UUID.randomUUID(), UserRole.Admin).futureValue

      result mustBe Right(b)
    }
  }

  "cancelBooking" should {
    "propagate Forbidden from the ownership check instead of cancelling" in {
      val b = Fixtures.booking(patientId = UUID.randomUUID())
      val userId = UUID.randomUUID()
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))
      when(patientRepository.findByUserId(userId)).thenReturn(Future.successful(Some(Fixtures.patientProfile())))

      val result = service.cancelBooking(b.id, userId, UserRole.Patient, None).futureValue

      result mustBe Left(BookingError.Forbidden)
      verify(bookingRepository, never).updateBookingStatus(any[UUID], any[BookingStatus], any[Option[String]])
    }

    "reject cancelling an already-completed booking" in {
      val patient = Fixtures.patientProfile()
      val userId = UUID.randomUUID()
      val b = Fixtures.booking(patientId = patient.id, status = BookingStatus.Completed)
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))
      when(patientRepository.findByUserId(userId)).thenReturn(Future.successful(Some(patient)))

      val result = service.cancelBooking(b.id, userId, UserRole.Patient, None).futureValue

      result mustBe Left(BookingError.InvalidStatus)
    }

    "cancel as the patient with CancelledByPatient" in {
      val patient = Fixtures.patientProfile()
      val userId = UUID.randomUUID()
      val b = Fixtures.booking(patientId = patient.id, status = BookingStatus.Confirmed)
      val cancelled = b.copy(status = BookingStatus.CancelledByPatient)
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)), Future.successful(Some(cancelled)))
      when(patientRepository.findByUserId(userId)).thenReturn(Future.successful(Some(patient)))
      when(bookingRepository.updateBookingStatus(b.id, BookingStatus.CancelledByPatient, None)).thenReturn(Future.successful(1))

      val result = service.cancelBooking(b.id, userId, UserRole.Patient, None).futureValue

      result mustBe Right(cancelled)
    }

    "cancel as the surgeon with CancelledBySurgeon" in {
      val surgeon = Fixtures.surgeonProfile()
      val userId = UUID.randomUUID()
      val b = Fixtures.booking(surgeonId = surgeon.id, status = BookingStatus.Pending)
      val cancelled = b.copy(status = BookingStatus.CancelledBySurgeon)
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)), Future.successful(Some(cancelled)))
      when(surgeonRepository.findByUserId(userId)).thenReturn(Future.successful(Some(surgeon)))
      when(bookingRepository.updateBookingStatus(b.id, BookingStatus.CancelledBySurgeon, None)).thenReturn(Future.successful(1))

      val result = service.cancelBooking(b.id, userId, UserRole.Surgeon, None).futureValue

      result mustBe Right(cancelled)
    }
  }

  "createReview" should {
    "fail with PatientNotFound when the caller has no patient profile" in {
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(None))
      when(bookingRepository.findBookingById(any[UUID])).thenReturn(Future.successful(Some(Fixtures.booking())))

      val result = service.createReview(UUID.randomUUID(), UUID.randomUUID(), 5, None, None, None, None, None, "Great").futureValue

      result mustBe Left(BookingError.PatientNotFound)
    }

    "fail with NotFound when the booking doesn't exist" in {
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(Fixtures.patientProfile())))
      when(bookingRepository.findBookingById(any[UUID])).thenReturn(Future.successful(None))

      val result = service.createReview(UUID.randomUUID(), UUID.randomUUID(), 5, None, None, None, None, None, "Great").futureValue

      result mustBe Left(BookingError.NotFound)
    }

    "forbid reviewing someone else's booking" in {
      val patient = Fixtures.patientProfile()
      val b = Fixtures.booking(patientId = UUID.randomUUID(), status = BookingStatus.Completed)
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))

      val result = service.createReview(UUID.randomUUID(), b.id, 5, None, None, None, None, None, "Great").futureValue

      result mustBe Left(BookingError.Forbidden)
    }

    "reject reviewing a booking that isn't completed yet" in {
      val patient = Fixtures.patientProfile()
      val b = Fixtures.booking(patientId = patient.id, status = BookingStatus.Confirmed)
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))

      val result = service.createReview(UUID.randomUUID(), b.id, 5, None, None, None, None, None, "Great").futureValue

      result mustBe Left(BookingError.BookingNotCompleted)
    }

    "reject a second review for the same booking" in {
      val patient = Fixtures.patientProfile()
      val b = Fixtures.booking(patientId = patient.id, status = BookingStatus.Completed)
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))
      when(bookingRepository.reviewExistsForBooking(b.id)).thenReturn(Future.successful(true))

      val result = service.createReview(UUID.randomUUID(), b.id, 5, None, None, None, None, None, "Great").futureValue

      result mustBe Left(BookingError.AlreadyReviewed)
    }

    "create the review for a completed, unreviewed booking owned by the patient" in {
      val patient = Fixtures.patientProfile()
      val b = Fixtures.booking(patientId = patient.id, status = BookingStatus.Completed)
      val createdReview = Fixtures.review(bookingId = b.id, patientId = patient.id, surgeonId = b.surgeonId)
      when(patientRepository.findByUserId(any[UUID])).thenReturn(Future.successful(Some(patient)))
      when(bookingRepository.findBookingById(b.id)).thenReturn(Future.successful(Some(b)))
      when(bookingRepository.reviewExistsForBooking(b.id)).thenReturn(Future.successful(false))
      when(bookingRepository.createReview(
        eqTo(b.id), eqTo(patient.id), eqTo(b.surgeonId), eqTo(5.toShort),
        any[Option[Short]], any[Option[Short]], any[Option[Short]], any[Option[Short]], any[Option[String]],
        eqTo("Great")
      )).thenReturn(Future.successful(createdReview))

      val result = service.createReview(UUID.randomUUID(), b.id, 5, None, None, None, None, None, "Great").futureValue

      result mustBe Right(createdReview)
    }
  }
}
