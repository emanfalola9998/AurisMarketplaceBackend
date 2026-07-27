// ─── app/co/auris/db/Tables.scala ────────────────────────────────────────────
//
// Slick table definitions for all Auris tables.
// Uses AurisPostgresProfile for UUID, enum, array, and JSONB support.
//
// Each Table class mirrors the SQL schema exactly.
// Column names use the snake_case names from Postgres.
// The * projection maps to the corresponding case class.

package co.auris.db

import co.auris.models._
import AurisPostgresProfile.api._
import play.api.libs.json.JsValue
import java.time.{LocalDate, LocalTime, OffsetDateTime}
import java.util.UUID

// ─── users ────────────────────────────────────────────────────────────────────

class UsersTable(tag: Tag) extends Table[User](tag, "users") {
  def id              = column[UUID]           ("id",               O.PrimaryKey)
  def email           = column[String]         ("email")
  def passwordHash    = column[String]         ("password_hash")
  def role            = column[UserRole]       ("role")
  def isActive        = column[Boolean]        ("is_active")
  def isEmailVerified = column[Boolean]        ("is_email_verified")
  def createdAt       = column[OffsetDateTime] ("created_at")
  def updatedAt       = column[OffsetDateTime] ("updated_at")

  def * = (id, email, passwordHash, role, isActive, isEmailVerified, createdAt, updatedAt).mapTo[User]
}

object Users extends TableQuery(new UsersTable(_))

// ─── refresh_tokens ───────────────────────────────────────────────────────────

class RefreshTokensTable(tag: Tag) extends Table[RefreshToken](tag, "refresh_tokens") {
  def id          = column[UUID]                    ("id",         O.PrimaryKey)
  def userId      = column[UUID]                    ("user_id")
  def tokenHash   = column[String]                  ("token_hash")
  def expiresAt   = column[OffsetDateTime]          ("expires_at")
  def createdAt   = column[OffsetDateTime]          ("created_at")
  def revokedAt   = column[Option[OffsetDateTime]]  ("revoked_at")
  def userAgent   = column[Option[String]]          ("user_agent")
  def ipAddress   = column[Option[String]]          ("ip_address")

  def user = foreignKey("fk_refresh_tokens_user", userId, Users)(_.id, onDelete = ForeignKeyAction.Cascade)

  def * = (id, userId, tokenHash, expiresAt, createdAt, revokedAt, userAgent, ipAddress).mapTo[RefreshToken]
}

object RefreshTokens extends TableQuery(new RefreshTokensTable(_))

// ─── email_verifications ─────────────────────────────────────────────────────

class EmailVerificationsTable(tag: Tag) extends Table[EmailVerification](tag, "email_verifications") {
  def id        = column[UUID]                   ("id",        O.PrimaryKey)
  def userId    = column[UUID]                   ("user_id")
  def token     = column[String]                 ("token")
  def expiresAt = column[OffsetDateTime]         ("expires_at")
  def usedAt    = column[Option[OffsetDateTime]] ("used_at")
  def createdAt = column[OffsetDateTime]         ("created_at")

  def * = (id, userId, token, expiresAt, usedAt, createdAt).mapTo[EmailVerification]
}

object EmailVerifications extends TableQuery(new EmailVerificationsTable(_))

// ─── password_resets ─────────────────────────────────────────────────────────

class PasswordResetsTable(tag: Tag) extends Table[PasswordReset](tag, "password_resets") {
  def id        = column[UUID]                   ("id",         O.PrimaryKey)
  def userId    = column[UUID]                   ("user_id")
  def tokenHash = column[String]                 ("token_hash")
  def expiresAt = column[OffsetDateTime]         ("expires_at")
  def usedAt    = column[Option[OffsetDateTime]] ("used_at")
  def createdAt = column[OffsetDateTime]         ("created_at")

  def * = (id, userId, tokenHash, expiresAt, usedAt, createdAt).mapTo[PasswordReset]
}

object PasswordResets extends TableQuery(new PasswordResetsTable(_))

// ─── patient_profiles ────────────────────────────────────────────────────────

class PatientProfilesTable(tag: Tag) extends Table[PatientProfile](tag, "patient_profiles") {
  def id                 = column[UUID]                       ("id",                  O.PrimaryKey)
  def userId             = column[UUID]                       ("user_id")
  def firstName          = column[String]                     ("first_name")
  def lastName           = column[String]                     ("last_name")
  def dateOfBirth        = column[Option[LocalDate]]          ("date_of_birth")
  def phone              = column[Option[String]]             ("phone")
  def onboardingComplete = column[Boolean]                    ("onboarding_complete")
  def procedureInterests = column[List[String]]               ("procedure_interests")
  def locationPreference = column[Option[String]]             ("location_preference")
  def consultPreference  = column[Option[ConsultationType]]   ("consult_preference")
  def budgetRange        = column[Option[String]]             ("budget_range")
  def timeline           = column[Option[String]]             ("timeline")
  def avatarUrl          = column[Option[String]]             ("avatar_url")
  def createdAt          = column[OffsetDateTime]             ("created_at")
  def updatedAt          = column[OffsetDateTime]             ("updated_at")

  def user = foreignKey("fk_patient_user", userId, Users)(_.id, onDelete = ForeignKeyAction.Cascade)

  def * = (
    id, userId, firstName, lastName, dateOfBirth, phone,
    onboardingComplete, procedureInterests, locationPreference,
    consultPreference, budgetRange, timeline, avatarUrl,
    createdAt, updatedAt
  ).mapTo[PatientProfile]
}

object PatientProfiles extends TableQuery(new PatientProfilesTable(_))

// ─── surgeon_profiles ────────────────────────────────────────────────────────

class SurgeonProfilesTable(tag: Tag) extends Table[SurgeonProfile](tag, "surgeon_profiles") {
  def id                = column[UUID]             ("id",                 O.PrimaryKey)
  def userId            = column[UUID]             ("user_id")
  def title             = column[String]           ("title")
  def firstName         = column[String]           ("first_name")
  def lastName          = column[String]           ("last_name")
  def gmcNumber         = column[String]           ("gmc_number")
  def qualifications    = column[List[String]]     ("qualifications")
  def medicalSchool     = column[Option[String]]   ("medical_school")
  def graduationYear    = column[Option[Short]]    ("graduation_year")
  def fellowships       = column[Option[String]]   ("fellowships")
  def specialty         = column[String]           ("specialty")
  def subspecialties    = column[List[String]]     ("subspecialties")
  def hospital          = column[String]           ("hospital")
  def city              = column[String]           ("city")
  def address           = column[Option[String]]   ("address")
  def yearsExperience   = column[Short]            ("years_experience")
  def languages         = column[List[String]]     ("languages")
  def bio               = column[Option[String]]   ("bio")
  def procedures        = column[List[String]]     ("procedures")
  def consultFeeClinic  = column[Option[BigDecimal]]("consult_fee_clinic")
  def consultFeeVirtual = column[Option[BigDecimal]]("consult_fee_virtual")
  def offersVirtual     = column[Boolean]          ("offers_virtual")
  def tier              = column[SurgeonTier]      ("tier")
  def profileComplete   = column[Boolean]          ("profile_complete")
  def profileLive       = column[Boolean]          ("profile_live")
  def avatarUrl         = column[Option[String]]   ("avatar_url")
  def rating            = column[BigDecimal]       ("rating")
  def reviewCount       = column[Int]              ("review_count")
  def consultationCount = column[Int]              ("consultation_count")
  def createdAt         = column[OffsetDateTime]   ("created_at")
  def updatedAt         = column[OffsetDateTime]   ("updated_at")

  def user = foreignKey("fk_surgeon_user", userId, Users)(_.id, onDelete = ForeignKeyAction.Cascade)

  // Scala tuples cap at 22 elements; split into two nested tuples.
  def * = (
    (id, userId, title, firstName, lastName, gmcNumber, qualifications,
     medicalSchool, graduationYear, fellowships, specialty, subspecialties,
     hospital, city, address, yearsExperience, languages, bio, procedures,
     consultFeeClinic, consultFeeVirtual, offersVirtual),
    (tier, profileComplete, profileLive, avatarUrl, rating, reviewCount,
     consultationCount, createdAt, updatedAt)
  ).shaped.<>({
    case (t1, t2) =>
      SurgeonProfile(
        id = t1._1, userId = t1._2, title = t1._3, firstName = t1._4,
        lastName = t1._5, gmcNumber = t1._6, qualifications = t1._7,
        medicalSchool = t1._8, graduationYear = t1._9, fellowships = t1._10,
        specialty = t1._11, subspecialties = t1._12, hospital = t1._13,
        city = t1._14, address = t1._15, yearsExperience = t1._16,
        languages = t1._17, bio = t1._18, procedures = t1._19,
        consultFeeClinic = t1._20, consultFeeVirtual = t1._21, offersVirtual = t1._22,
        tier = t2._1, profileComplete = t2._2, profileLive = t2._3,
        avatarUrl = t2._4, rating = t2._5, reviewCount = t2._6,
        consultationCount = t2._7, createdAt = t2._8, updatedAt = t2._9
      )
  }, { sp: SurgeonProfile =>
    Some((
      (sp.id, sp.userId, sp.title, sp.firstName, sp.lastName, sp.gmcNumber,
       sp.qualifications, sp.medicalSchool, sp.graduationYear, sp.fellowships,
       sp.specialty, sp.subspecialties, sp.hospital, sp.city, sp.address,
       sp.yearsExperience, sp.languages, sp.bio, sp.procedures,
       sp.consultFeeClinic, sp.consultFeeVirtual, sp.offersVirtual),
      (sp.tier, sp.profileComplete, sp.profileLive, sp.avatarUrl,
       sp.rating, sp.reviewCount, sp.consultationCount, sp.createdAt, sp.updatedAt)
    ))
  })
}

object SurgeonProfiles extends TableQuery(new SurgeonProfilesTable(_))

// ─── surgeon_applications ────────────────────────────────────────────────────

class SurgeonApplicationsTable(tag: Tag) extends Table[SurgeonApplication](tag, "surgeon_applications") {
  def id            = column[UUID]                       ("id",          O.PrimaryKey)
  def surgeonId     = column[UUID]                       ("surgeon_id")
  def status        = column[ApplicationStatus]          ("status")
  def reviewerId    = column[Option[UUID]]               ("reviewer_id")
  def reviewerNotes = column[Option[String]]             ("reviewer_notes")
  def flags         = column[List[String]]               ("flags")
  def score         = column[Option[Short]]              ("score")
  def cvUrl         = column[Option[String]]             ("cv_url")
  def indemnityUrl  = column[Option[String]]             ("indemnity_url")
  def photoUrl      = column[Option[String]]             ("photo_url")
  def submittedAt   = column[OffsetDateTime]             ("submitted_at")
  def reviewedAt    = column[Option[OffsetDateTime]]     ("reviewed_at")
  def approvedAt    = column[Option[OffsetDateTime]]     ("approved_at")

  def surgeon  = foreignKey("fk_app_surgeon",  surgeonId,  SurgeonProfiles)(_.id, onDelete = ForeignKeyAction.Cascade)
  def reviewer = foreignKey("fk_app_reviewer", reviewerId, Users)(_.id.?)

  def * = (
    id, surgeonId, status, reviewerId, reviewerNotes, flags, score,
    cvUrl, indemnityUrl, photoUrl, submittedAt, reviewedAt, approvedAt
  ).mapTo[SurgeonApplication]
}

object SurgeonApplications extends TableQuery(new SurgeonApplicationsTable(_))

// ─── surgeon_availability ────────────────────────────────────────────────────

class SurgeonAvailabilityTable(tag: Tag) extends Table[SurgeonAvailability](tag, "surgeon_availability") {
  def id            = column[UUID]       ("id",           O.PrimaryKey)
  def surgeonId     = column[UUID]       ("surgeon_id")
  def dayOfWeek     = column[Short]      ("day_of_week")
  def startTime     = column[LocalTime]  ("start_time")
  def endTime       = column[LocalTime]  ("end_time")
  def bufferMinutes = column[Short]      ("buffer_minutes")
  def isActive      = column[Boolean]    ("is_active")

  def * = (id, surgeonId, dayOfWeek, startTime, endTime, bufferMinutes, isActive).mapTo[SurgeonAvailability]
}

object SurgeonAvailabilities extends TableQuery(new SurgeonAvailabilityTable(_))

// ─── saved_surgeons ───────────────────────────────────────────────────────────

class SavedSurgeonsTable(tag: Tag) extends Table[SavedSurgeon](tag, "saved_surgeons") {
  def patientId = column[UUID]          ("patient_id")
  def surgeonId = column[UUID]          ("surgeon_id")
  def savedAt   = column[OffsetDateTime]("saved_at")

  def pk = primaryKey("pk_saved_surgeons", (patientId, surgeonId))
  def patient = foreignKey("fk_saved_patient", patientId, PatientProfiles)(_.id, onDelete = ForeignKeyAction.Cascade)
  def surgeon = foreignKey("fk_saved_surgeon", surgeonId, SurgeonProfiles)(_.id, onDelete = ForeignKeyAction.Cascade)

  def * = (patientId, surgeonId, savedAt).mapTo[SavedSurgeon]
}

object SavedSurgeons extends TableQuery(new SavedSurgeonsTable(_))

// ─── enquiries ───────────────────────────────────────────────────────────────

class EnquiriesTable(tag: Tag) extends Table[Enquiry](tag, "enquiries") {
  def id                = column[UUID]                    ("id",               O.PrimaryKey)
  def patientId         = column[UUID]                    ("patient_id")
  def surgeonId         = column[UUID]                    ("surgeon_id")
  def procedureInterest = column[Option[String]]          ("procedure_interest")
  def goals             = column[Option[String]]          ("goals")
  def previousSurgery   = column[Boolean]                 ("previous_surgery")
  def previousDetails   = column[Option[String]]          ("previous_details")
  def preferredDate     = column[Option[LocalDate]]       ("preferred_date")
  def preferredTime     = column[Option[LocalTime]]       ("preferred_time")
  def consultationType  = column[ConsultationType]        ("consultation_type")
  def status            = column[EnquiryStatus]           ("status")
  def fee               = column[BigDecimal]              ("fee")
  def heardAbout        = column[Option[String]]          ("heard_about")
  def surgeonNotes      = column[Option[String]]          ("surgeon_notes")
  def createdAt         = column[OffsetDateTime]          ("created_at")
  def updatedAt         = column[OffsetDateTime]          ("updated_at")

  def * = (
    id, patientId, surgeonId, procedureInterest, goals,
    previousSurgery, previousDetails, preferredDate, preferredTime,
    consultationType, status, fee, heardAbout, surgeonNotes,
    createdAt, updatedAt
  ).mapTo[Enquiry]
}

object Enquiries extends TableQuery(new EnquiriesTable(_))

// ─── bookings ────────────────────────────────────────────────────────────────

class BookingsTable(tag: Tag) extends Table[Booking](tag, "bookings") {
  def id               = column[UUID]                    ("id",               O.PrimaryKey)
  def enquiryId        = column[Option[UUID]]            ("enquiry_id")
  def patientId        = column[UUID]                    ("patient_id")
  def surgeonId        = column[UUID]                    ("surgeon_id")
  def consultationType = column[ConsultationType]        ("consultation_type")
  def scheduledAt      = column[OffsetDateTime]          ("scheduled_at")
  def durationMinutes  = column[Short]                   ("duration_minutes")
  def status           = column[BookingStatus]           ("status")
  def fee              = column[BigDecimal]              ("fee")
  def stripePaymentId  = column[Option[String]]          ("stripe_payment_id")
  def stripeRefundId   = column[Option[String]]          ("stripe_refund_id")
  def paidAt           = column[Option[OffsetDateTime]]  ("paid_at")
  def cancelledAt      = column[Option[OffsetDateTime]]  ("cancelled_at")
  def cancellationNote = column[Option[String]]          ("cancellation_note")
  def videoLink        = column[Option[String]]          ("video_link")
  def notes            = column[Option[String]]          ("notes")
  def createdAt        = column[OffsetDateTime]          ("created_at")
  def updatedAt        = column[OffsetDateTime]          ("updated_at")

  def * = (
    id, enquiryId, patientId, surgeonId, consultationType,
    scheduledAt, durationMinutes, status, fee,
    stripePaymentId, stripeRefundId, paidAt,
    cancelledAt, cancellationNote, videoLink, notes,
    createdAt, updatedAt
  ).mapTo[Booking]
}

object Bookings extends TableQuery(new BookingsTable(_))

// ─── reviews ─────────────────────────────────────────────────────────────────

class ReviewsTable(tag: Tag) extends Table[Review](tag, "reviews") {
  def id                  = column[UUID]                   ("id",                   O.PrimaryKey)
  def bookingId           = column[UUID]                   ("booking_id")
  def patientId           = column[UUID]                   ("patient_id")
  def surgeonId           = column[UUID]                   ("surgeon_id")
  def rating              = column[Short]                  ("rating")
  def ratingResults       = column[Option[Short]]          ("rating_results")
  def ratingCommunication = column[Option[Short]]          ("rating_communication")
  def ratingAftercare     = column[Option[Short]]          ("rating_aftercare")
  def ratingValue         = column[Option[Short]]          ("rating_value")
  def procedure           = column[Option[String]]         ("procedure")
  def body                = column[String]                 ("body")
  def isVerified          = column[Boolean]                ("is_verified")
  def isPublished         = column[Boolean]                ("is_published")
  def publishedAt         = column[Option[OffsetDateTime]] ("published_at")
  def createdAt           = column[OffsetDateTime]         ("created_at")

  def * = (
    id, bookingId, patientId, surgeonId, rating,
    ratingResults, ratingCommunication, ratingAftercare, ratingValue,
    procedure, body, isVerified, isPublished, publishedAt, createdAt
  ).mapTo[Review]
}

object Reviews extends TableQuery(new ReviewsTable(_))

// ─── messages ────────────────────────────────────────────────────────────────

class MessagesTable(tag: Tag) extends Table[Message](tag, "messages") {
  def id          = column[UUID]                   ("id",          O.PrimaryKey)
  def senderId    = column[UUID]                   ("sender_id")
  def recipientId = column[UUID]                   ("recipient_id")
  def body        = column[String]                 ("body")
  def isRead      = column[Boolean]                ("is_read")
  def readAt      = column[Option[OffsetDateTime]] ("read_at")
  def createdAt   = column[OffsetDateTime]         ("created_at")

  def * = (id, senderId, recipientId, body, isRead, readAt, createdAt).mapTo[Message]
}

object Messages extends TableQuery(new MessagesTable(_))

// ─── notifications ───────────────────────────────────────────────────────────

class NotificationsTable(tag: Tag) extends Table[Notification](tag, "notifications") {
  def id        = column[UUID]                   ("id",        O.PrimaryKey)
  def userId    = column[UUID]                   ("user_id")
  def `type`    = column[NotificationType]       ("type")
  def title     = column[String]                 ("title")
  def body      = column[String]                 ("body")
  def link      = column[Option[String]]         ("link")
  def isRead    = column[Boolean]                ("is_read")
  def readAt    = column[Option[OffsetDateTime]] ("read_at")
  def createdAt = column[OffsetDateTime]         ("created_at")

  def * = (id, userId, `type`, title, body, link, isRead, readAt, createdAt).mapTo[Notification]
}

object Notifications extends TableQuery(new NotificationsTable(_))

// ─── surgeon_portfolio ───────────────────────────────────────────────────────

class PortfolioTable(tag: Tag) extends Table[PortfolioItem](tag, "surgeon_portfolio") {
  def id           = column[UUID]          ("id",         O.PrimaryKey)
  def surgeonId    = column[UUID]          ("surgeon_id")
  def beforeUrl    = column[String]        ("before_url")
  def afterUrl     = column[String]        ("after_url")
  def procedure    = column[Option[String]]("procedure")
  def caption      = column[Option[String]]("caption")
  def consentGiven = column[Boolean]       ("consent_given")
  def isPublished  = column[Boolean]       ("is_published")
  def createdAt    = column[OffsetDateTime]("created_at")

  def * = (id, surgeonId, beforeUrl, afterUrl, procedure, caption, consentGiven, isPublished, createdAt).mapTo[PortfolioItem]
}

object Portfolio extends TableQuery(new PortfolioTable(_))

// ─── audit_log ────────────────────────────────────────────────────────────────

class AuditLogTable(tag: Tag) extends Table[AuditLogEntry](tag, "audit_log") {
  def id         = column[UUID]                   ("id",          O.PrimaryKey)
  def actorId    = column[UUID]                   ("actor_id")
  def action     = column[String]                 ("action")
  def targetType = column[String]                 ("target_type")
  def targetId   = column[UUID]                   ("target_id")
  def metadata   = column[Option[JsValue]]        ("metadata")
  def ipAddress  = column[Option[String]]         ("ip_address")
  def createdAt  = column[OffsetDateTime]         ("created_at")

  def * = (id, actorId, action, targetType, targetId, metadata, ipAddress, createdAt).mapTo[AuditLogEntry]
}

object AuditLog extends TableQuery(new AuditLogTable(_))