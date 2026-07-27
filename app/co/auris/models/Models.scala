// ─── app/co/auris/models/Models.scala ────────────────────────────────────────
//
// All domain case classes for Auris.
// Each case class maps 1:1 to a database table row.
// Play JSON Reads/Writes are derived automatically via Json.format[T].

package co.auris.models

import java.time.{LocalDate, LocalTime, OffsetDateTime}
import java.util.UUID
import play.api.libs.json._

// ─── Shared ───────────────────────────────────────────────────────────────────

// We use OffsetDateTime for all timestamp columns to preserve timezone info.
// Slick-pg's PgDate2Support maps TIMESTAMPTZ ↔ OffsetDateTime.
// For plain DATE columns we use LocalDate, TIME columns use LocalTime.

// ─── User ─────────────────────────────────────────────────────────────────────

case class User(
                 id:              UUID,
                 email:           String,
                 passwordHash:    String,
                 role:            UserRole,
                 isActive:        Boolean         = true,
                 isEmailVerified: Boolean         = false,
                 createdAt:       OffsetDateTime,
                 updatedAt:       OffsetDateTime
               )

object User {
  // Never expose passwordHash over the wire — use UserResponse instead
  implicit val format: OFormat[User] = Json.format[User]
}

// ─── Refresh token ────────────────────────────────────────────────────────────

case class RefreshToken(
                         id:         UUID,
                         userId:     UUID,
                         tokenHash:  String,
                         expiresAt:  OffsetDateTime,
                         createdAt:  OffsetDateTime,
                         revokedAt:  Option[OffsetDateTime] = None,
                         userAgent:  Option[String]         = None,
                         ipAddress:  Option[String]         = None
                       )

// ─── Email verification ───────────────────────────────────────────────────────

case class EmailVerification(
                              id:        UUID,
                              userId:    UUID,
                              token:     String,
                              expiresAt: OffsetDateTime,
                              usedAt:    Option[OffsetDateTime] = None,
                              createdAt: OffsetDateTime
                            )

// ─── Password reset ───────────────────────────────────────────────────────────

case class PasswordReset(
                          id:        UUID,
                          userId:    UUID,
                          tokenHash: String,
                          expiresAt: OffsetDateTime,
                          usedAt:    Option[OffsetDateTime] = None,
                          createdAt: OffsetDateTime
                        )

// ─── Patient profile ──────────────────────────────────────────────────────────

case class PatientProfile(
                           id:                 UUID,
                           userId:             UUID,
                           firstName:          String,
                           lastName:           String,
                           dateOfBirth:        Option[LocalDate]        = None,
                           phone:              Option[String]           = None,
                           onboardingComplete: Boolean                  = false,
                           procedureInterests: List[String]             = Nil,
                           locationPreference: Option[String]           = None,
                           consultPreference:  Option[ConsultationType] = None,
                           budgetRange:        Option[String]           = None,
                           timeline:           Option[String]           = None,
                           avatarUrl:          Option[String]           = None,
                           createdAt:          OffsetDateTime,
                           updatedAt:          OffsetDateTime
                         ) {
  def fullName: String = s"$firstName $lastName"
}

object PatientProfile {
  implicit val format: OFormat[PatientProfile] = Json.format[PatientProfile]
}

// ─── Surgeon profile ──────────────────────────────────────────────────────────

case class SurgeonProfile(
                           id:                UUID,
                           userId:            UUID,
                           title:             String              = "Dr.",
                           firstName:         String,
                           lastName:          String,
                           gmcNumber:         String,
                           qualifications:    List[String]        = Nil,
                           medicalSchool:     Option[String]      = None,
                           graduationYear:    Option[Short]       = None,
                           fellowships:       Option[String]      = None,
                           specialty:         String,
                           subspecialties:    List[String]        = Nil,
                           hospital:          String,
                           city:              String,
                           address:           Option[String]      = None,
                           yearsExperience:   Short               = 0,
                           languages:         List[String]        = List("English"),
                           bio:               Option[String]      = None,
                           procedures:        List[String]        = Nil,
                           consultFeeClinic:  Option[BigDecimal]  = None,
                           consultFeeVirtual: Option[BigDecimal]  = None,
                           offersVirtual:     Boolean             = true,
                           tier:              SurgeonTier         = SurgeonTier.Essential,
                           profileComplete:   Boolean             = false,
                           profileLive:       Boolean             = false,
                           avatarUrl:         Option[String]      = None,
                           rating:            BigDecimal          = BigDecimal("0.00"),
                           reviewCount:       Int                 = 0,
                           consultationCount: Int                 = 0,
                           createdAt:         OffsetDateTime,
                           updatedAt:         OffsetDateTime
                         ) {
  def fullName: String  = s"$title $firstName $lastName"
  def displayName: String = fullName
}

object SurgeonProfile {
  implicit val writes: OWrites[SurgeonProfile] = sp => Json.obj(
    "id"                -> sp.id,
    "userId"            -> sp.userId,
    "title"             -> sp.title,
    "firstName"         -> sp.firstName,
    "lastName"          -> sp.lastName,
    "gmcNumber"         -> sp.gmcNumber,
    "qualifications"    -> sp.qualifications,
    "medicalSchool"     -> sp.medicalSchool,
    "graduationYear"    -> sp.graduationYear.map(_.toInt),
    "fellowships"       -> sp.fellowships,
    "specialty"         -> sp.specialty,
    "subspecialties"    -> sp.subspecialties,
    "hospital"          -> sp.hospital,
    "city"              -> sp.city,
    "address"           -> sp.address,
    "yearsExperience"   -> sp.yearsExperience.toInt,
    "languages"         -> sp.languages,
    "bio"               -> sp.bio,
    "procedures"        -> sp.procedures,
    "consultFeeClinic"  -> sp.consultFeeClinic,
    "consultFeeVirtual" -> sp.consultFeeVirtual,
    "offersVirtual"     -> sp.offersVirtual,
    "tier"              -> sp.tier.entryName,
    "profileComplete"   -> sp.profileComplete,
    "profileLive"       -> sp.profileLive,
    "avatarUrl"         -> sp.avatarUrl,
    "rating"            -> sp.rating,
    "reviewCount"       -> sp.reviewCount,
    "consultationCount" -> sp.consultationCount,
    "createdAt"         -> sp.createdAt,
    "updatedAt"         -> sp.updatedAt
  )
}

// ─── Surgeon application ──────────────────────────────────────────────────────

case class SurgeonApplication(
                               id:            UUID,
                               surgeonId:     UUID,
                               status:        ApplicationStatus         = ApplicationStatus.Pending,
                               reviewerId:    Option[UUID]              = None,
                               reviewerNotes: Option[String]            = None,
                               flags:         List[String]              = Nil,
                               score:         Option[Short]             = None,
                               cvUrl:         Option[String]            = None,
                               indemnityUrl:  Option[String]            = None,
                               photoUrl:      Option[String]            = None,
                               submittedAt:   OffsetDateTime,
                               reviewedAt:    Option[OffsetDateTime]    = None,
                               approvedAt:    Option[OffsetDateTime]    = None
                             )

object SurgeonApplication {
  implicit val format: OFormat[SurgeonApplication] = Json.format[SurgeonApplication]
}

// ─── Surgeon availability ─────────────────────────────────────────────────────

case class SurgeonAvailability(
                                id:            UUID,
                                surgeonId:     UUID,
                                dayOfWeek:     Short,     // 1=Mon ... 7=Sun ISO
                                startTime:     LocalTime,
                                endTime:       LocalTime,
                                bufferMinutes: Short    = 30,
                                isActive:      Boolean  = true
                              )

// ─── Surgeon blocked slot ─────────────────────────────────────────────────────

case class SurgeonBlockedSlot(
                               id:           UUID,
                               surgeonId:    UUID,
                               blockedAt:    OffsetDateTime,
                               durationMins: Short          = 60,
                               reason:       Option[String] = None
                             )

// ─── Saved surgeon ────────────────────────────────────────────────────────────

case class SavedSurgeon(
                         patientId: UUID,
                         surgeonId: UUID,
                         savedAt:   OffsetDateTime
                       )

// ─── Enquiry ─────────────────────────────────────────────────────────────────

case class Enquiry(
                    id:               UUID,
                    patientId:        UUID,
                    surgeonId:        UUID,
                    procedureInterest: Option[String]          = None,
                    goals:            Option[String]           = None,
                    previousSurgery:  Boolean                  = false,
                    previousDetails:  Option[String]           = None,
                    preferredDate:    Option[LocalDate]        = None,
                    preferredTime:    Option[LocalTime]        = None,
                    consultationType: ConsultationType         = ConsultationType.InClinic,
                    status:           EnquiryStatus            = EnquiryStatus.Pending,
                    fee:              BigDecimal,
                    heardAbout:       Option[String]           = None,
                    surgeonNotes:     Option[String]           = None,
                    createdAt:        OffsetDateTime,
                    updatedAt:        OffsetDateTime
                  )

object Enquiry {
  implicit val format: OFormat[Enquiry] = Json.format[Enquiry]
}

// ─── Booking ──────────────────────────────────────────────────────────────────

case class Booking(
                    id:               UUID,
                    enquiryId:        Option[UUID]            = None,
                    patientId:        UUID,
                    surgeonId:        UUID,
                    consultationType: ConsultationType,
                    scheduledAt:      OffsetDateTime,
                    durationMinutes:  Short                   = 60,
                    status:           BookingStatus           = BookingStatus.Pending,
                    fee:              BigDecimal,
                    stripePaymentId:  Option[String]          = None,
                    stripeRefundId:   Option[String]          = None,
                    paidAt:           Option[OffsetDateTime]  = None,
                    cancelledAt:      Option[OffsetDateTime]  = None,
                    cancellationNote: Option[String]          = None,
                    videoLink:        Option[String]          = None,
                    notes:            Option[String]          = None,
                    createdAt:        OffsetDateTime,
                    updatedAt:        OffsetDateTime
                  )

object Booking {
  implicit val format: OFormat[Booking] = Json.format[Booking]
}

// ─── Review ───────────────────────────────────────────────────────────────────

case class Review(
                   id:                  UUID,
                   bookingId:           UUID,
                   patientId:           UUID,
                   surgeonId:           UUID,
                   rating:              Short,
                   ratingResults:       Option[Short]         = None,
                   ratingCommunication: Option[Short]         = None,
                   ratingAftercare:     Option[Short]         = None,
                   ratingValue:         Option[Short]         = None,
                   procedure:           Option[String]        = None,
                   body:                String,
                   isVerified:          Boolean               = true,
                   isPublished:         Boolean               = false,
                   publishedAt:         Option[OffsetDateTime] = None,
                   createdAt:           OffsetDateTime
                 )

object Review {
  implicit val format: OFormat[Review] = Json.format[Review]
}

// ─── Message ─────────────────────────────────────────────────────────────────

case class Message(
                    id:          UUID,
                    senderId:    UUID,
                    recipientId: UUID,
                    body:        String,
                    isRead:      Boolean                  = false,
                    readAt:      Option[OffsetDateTime]   = None,
                    createdAt:   OffsetDateTime
                  )

object Message {
  implicit val format: OFormat[Message] = Json.format[Message]
}

case class ConversationSummary(
                                partnerId:   UUID,
                                lastMessage: Message,
                                unreadCount: Int
                              )

object ConversationSummary {
  implicit val format: OFormat[ConversationSummary] = Json.format[ConversationSummary]
}

// ─── Notification ─────────────────────────────────────────────────────────────

case class Notification(
                         id:        UUID,
                         userId:    UUID,
                         `type`:    NotificationType,
                         title:     String,
                         body:      String,
                         link:      Option[String]          = None,
                         isRead:    Boolean                 = false,
                         readAt:    Option[OffsetDateTime]  = None,
                         createdAt: OffsetDateTime
                       )

object Notification {
  implicit val format: OFormat[Notification] = Json.format[Notification]
}

// ─── Surgeon portfolio item ────────────────────────────────────────────────────

case class PortfolioItem(
                          id:           UUID,
                          surgeonId:    UUID,
                          beforeUrl:    String,
                          afterUrl:     String,
                          procedure:    Option[String] = None,
                          caption:      Option[String] = None,
                          consentGiven: Boolean        = false,
                          isPublished:  Boolean        = false,
                          createdAt:    OffsetDateTime
                        )

object PortfolioItem {
  implicit val format: OFormat[PortfolioItem] = Json.format[PortfolioItem]
}

// ─── Audit log entry ──────────────────────────────────────────────────────────

case class AuditLogEntry(
                          id:         UUID,
                          actorId:    UUID,
                          action:     String,
                          targetType: String,
                          targetId:   UUID,
                          metadata:   Option[play.api.libs.json.JsValue] = None,
                          ipAddress:  Option[String]                     = None,
                          createdAt:  OffsetDateTime
                        )

// ─── API request/response models ──────────────────────────────────────────────
// These are the shapes going over the wire — separate from DB models.
// Never expose passwordHash, internal IDs, or raw Postgres fields directly.

case class SignInRequest(email: String, password: String)
object SignInRequest { implicit val reads: Reads[SignInRequest] = Json.reads[SignInRequest] }

case class SignUpRequest(email: String, password: String, role: UserRole)
object SignUpRequest { implicit val reads: Reads[SignUpRequest] = Json.reads[SignUpRequest] }

case class TokenResponse(
                          accessToken:  String,
                          refreshToken: String,
                          tokenType:    String    = "Bearer",
                          expiresIn:    Long      // seconds
                        )
object TokenResponse { implicit val writes: OWrites[TokenResponse] = Json.writes[TokenResponse] }

case class RefreshRequest(refreshToken: String)
object RefreshRequest { implicit val reads: Reads[RefreshRequest] = Json.reads[RefreshRequest] }

// ── User response (safe — no password hash) ──────────────────────────────────

case class UserResponse(
                         id:              UUID,
                         email:           String,
                         role:            UserRole,
                         isEmailVerified: Boolean,
                         patient:         Option[PatientProfile]  = None,
                         surgeon:         Option[SurgeonProfile]  = None
                       )
object UserResponse {
  implicit val writes: OWrites[UserResponse] = Json.writes[UserResponse]

  def from(user: User, patient: Option[PatientProfile], surgeon: Option[SurgeonProfile]): UserResponse =
    UserResponse(
      id              = user.id,
      email           = user.email,
      role            = user.role,
      isEmailVerified = user.isEmailVerified,
      patient         = patient,
      surgeon         = surgeon
    )
}

// ── Surgeon search result ─────────────────────────────────────────────────────

case class SurgeonSearchResult(
                                id:                UUID,
                                displayName:       String,
                                specialty:         String,
                                city:              String,
                                tier:              SurgeonTier,
                                rating:            BigDecimal,
                                reviewCount:       Int,
                                consultFeeClinic:  Option[BigDecimal],
                                consultFeeVirtual: Option[BigDecimal],
                                offersVirtual:     Boolean,
                                procedures:        List[String],
                                avatarUrl:         Option[String],
                                badges:            List[String]
                              )
object SurgeonSearchResult {
  implicit val writes: OWrites[SurgeonSearchResult] = Json.writes[SurgeonSearchResult]

  def from(s: SurgeonProfile): SurgeonSearchResult = SurgeonSearchResult(
    id                = s.id,
    displayName       = s.displayName,
    specialty         = s.specialty,
    city              = s.city,
    tier              = s.tier,
    rating            = s.rating,
    reviewCount       = s.reviewCount,
    consultFeeClinic  = s.consultFeeClinic,
    consultFeeVirtual = s.consultFeeVirtual,
    offersVirtual     = s.offersVirtual,
    procedures        = s.procedures,
    avatarUrl         = s.avatarUrl,
    badges            = List(
      if (s.tier == SurgeonTier.Gold)  Some("Gold Standard") else None,
      if (s.tier == SurgeonTier.Elite) Some("Auris Elite")   else None
    ).flatten
  )
}

// ── Pagination ────────────────────────────────────────────────────────────────

case class Page[A](
                    items:      List[A],
                    totalCount: Long,
                    page:       Int,
                    pageSize:   Int
                  ) {
  def totalPages: Int = Math.ceil(totalCount.toDouble / pageSize).toInt
  def hasNext:    Boolean = page < totalPages
  def hasPrev:    Boolean = page > 1
}

object Page {
  implicit def writes[A: Writes]: OWrites[Page[A]] = Json.writes[Page[A]]
}

// ── API error ─────────────────────────────────────────────────────────────────

case class ApiError(
                     code:    String,
                     message: String,
                     details: Option[JsValue] = None
                   )
object ApiError {
  implicit val writes: OWrites[ApiError] = Json.writes[ApiError]

  val Unauthorized:  ApiError = ApiError("UNAUTHORIZED",   "Authentication required.")
  val Forbidden:     ApiError = ApiError("FORBIDDEN",      "You do not have permission to perform this action.")
  val NotFound:      ApiError = ApiError("NOT_FOUND",      "The requested resource was not found.")
  val InternalError: ApiError = ApiError("INTERNAL_ERROR", "An unexpected error occurred.")
  val InvalidToken:  ApiError = ApiError("INVALID_TOKEN",  "The provided token is invalid or expired.")
}