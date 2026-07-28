// ─── test/co/auris/support/Fixtures.scala ─────────────────────────────────────
//
// Minimal, valid model instances for unit tests. Every builder takes only the
// fields a test is likely to want to vary — everything else gets a sane,
// deterministic default so specs don't drown in boilerplate.

package co.auris.support

import co.auris.models._

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

object Fixtures {

  val now: OffsetDateTime = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC)

  def user(
            id:       UUID    = UUID.randomUUID(),
            email:    String  = "patient@example.com",
            role:     UserRole = UserRole.Patient,
            isActive: Boolean = true,
            passwordHash: String = "$2a$04$dummyHashDummyHashDummyHashDummyHashDummyHas"
          ): User =
    User(
      id              = id,
      email           = email,
      passwordHash    = passwordHash,
      role            = role,
      isActive        = isActive,
      isEmailVerified = false,
      createdAt       = now,
      updatedAt       = now
    )

  def refreshToken(
                     userId:    UUID,
                     tokenHash: String = "hash",
                     revokedAt: Option[OffsetDateTime] = None
                   ): RefreshToken =
    RefreshToken(
      id        = UUID.randomUUID(),
      userId    = userId,
      tokenHash = tokenHash,
      expiresAt = now.plusDays(7),
      createdAt = now,
      revokedAt = revokedAt
    )

  def emailVerification(userId: UUID, token: String = "verify-token"): EmailVerification =
    EmailVerification(
      id        = UUID.randomUUID(),
      userId    = userId,
      token     = token,
      expiresAt = now.plusDays(1),
      createdAt = now
    )

  def passwordReset(userId: UUID, tokenHash: String = "reset-hash"): PasswordReset =
    PasswordReset(
      id        = UUID.randomUUID(),
      userId    = userId,
      tokenHash = tokenHash,
      expiresAt = now.plusHours(1),
      createdAt = now
    )

  def patientProfile(
                       id:                 UUID = UUID.randomUUID(),
                       userId:             UUID = UUID.randomUUID(),
                       onboardingComplete: Boolean = false
                     ): PatientProfile =
    PatientProfile(
      id                 = id,
      userId             = userId,
      firstName          = "Charlotte",
      lastName           = "Mitchell",
      onboardingComplete = onboardingComplete,
      createdAt          = now,
      updatedAt          = now
    )

  def surgeonProfile(
                       id:              UUID          = UUID.randomUUID(),
                       userId:          UUID          = UUID.randomUUID(),
                       profileLive:     Boolean       = true,
                       profileComplete: Boolean       = true,
                       consultFeeClinic:  Option[BigDecimal] = Some(BigDecimal(350)),
                       consultFeeVirtual: Option[BigDecimal] = Some(BigDecimal(150)),
                       stripeCustomerId:   Option[String]     = None,
                       subscriptionStatus: SubscriptionStatus = SubscriptionStatus.None
                     ): SurgeonProfile =
    SurgeonProfile(
      id                = id,
      userId            = userId,
      title             = "Mr.",
      firstName         = "James",
      lastName          = "Harrison",
      gmcNumber         = "7654321",
      specialty         = "Plastic & Reconstructive Surgery",
      hospital          = "King's College Hospital",
      city              = "London",
      consultFeeClinic  = consultFeeClinic,
      consultFeeVirtual = consultFeeVirtual,
      tier              = SurgeonTier.Elite,
      profileComplete   = profileComplete,
      profileLive       = profileLive,
      stripeCustomerId    = stripeCustomerId,
      subscriptionStatus  = subscriptionStatus,
      createdAt         = now,
      updatedAt         = now
    )

  def surgeonApplication(
                           id:        UUID              = UUID.randomUUID(),
                           surgeonId: UUID              = UUID.randomUUID(),
                           status:    ApplicationStatus = ApplicationStatus.Pending
                         ): SurgeonApplication =
    SurgeonApplication(
      id          = id,
      surgeonId   = surgeonId,
      status      = status,
      submittedAt = now
    )

  def enquiry(
               id:        UUID          = UUID.randomUUID(),
               patientId: UUID          = UUID.randomUUID(),
               surgeonId: UUID          = UUID.randomUUID(),
               status:    EnquiryStatus = EnquiryStatus.Pending,
               consultationType: ConsultationType = ConsultationType.InClinic,
               fee:       BigDecimal    = BigDecimal(350)
             ): Enquiry =
    Enquiry(
      id               = id,
      patientId        = patientId,
      surgeonId        = surgeonId,
      consultationType = consultationType,
      status           = status,
      fee              = fee,
      createdAt        = now,
      updatedAt        = now
    )

  def booking(
               id:        UUID          = UUID.randomUUID(),
               patientId: UUID          = UUID.randomUUID(),
               surgeonId: UUID          = UUID.randomUUID(),
               status:    BookingStatus = BookingStatus.Pending,
               consultationType: ConsultationType = ConsultationType.InClinic,
               fee:       BigDecimal    = BigDecimal(350)
             ): Booking =
    Booking(
      id               = id,
      patientId        = patientId,
      surgeonId        = surgeonId,
      consultationType = consultationType,
      scheduledAt      = now.plusDays(14),
      status           = status,
      fee              = fee,
      createdAt        = now,
      updatedAt        = now
    )

  def platformFee(
                   id:        UUID            = UUID.randomUUID(),
                   surgeonId: UUID            = UUID.randomUUID(),
                   bookingId: Option[UUID]    = None,
                   feeType:   PlatformFeeType = PlatformFeeType.Transaction,
                   amount:    BigDecimal      = BigDecimal("1.05")
                 ): PlatformFee =
    PlatformFee(
      id        = id,
      surgeonId = surgeonId,
      bookingId = bookingId,
      feeType   = feeType,
      amount    = amount,
      createdAt = now
    )

  def review(
              id:        UUID = UUID.randomUUID(),
              bookingId: UUID = UUID.randomUUID(),
              patientId: UUID = UUID.randomUUID(),
              surgeonId: UUID = UUID.randomUUID(),
              rating:    Short = 5
            ): Review =
    Review(
      id         = id,
      bookingId  = bookingId,
      patientId  = patientId,
      surgeonId  = surgeonId,
      rating     = rating,
      body       = "Excellent care throughout.",
      createdAt  = now
    )
}
