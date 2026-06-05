// ─── app/co/auris/models/Enums.scala ─────────────────────────────────────────
//
// All domain enums as enumeratum StringEnums.
// These map 1:1 to the Postgres enum types in the schema.
// The .entryName on each value is the exact string written to Postgres.

package co.auris.models

import enumeratum._
import play.api.libs.json._

// ─── UserRole ─────────────────────────────────────────────────────────────────

sealed trait UserRole extends EnumEntry {
  override def entryName: String = toString.toLowerCase
}
object UserRole extends Enum[UserRole] {
  case object Patient extends UserRole
  case object Surgeon extends UserRole
  case object Admin   extends UserRole
  val values: IndexedSeq[UserRole] = findValues
  override def withName(s: String): UserRole = withNameInsensitive(s)

  implicit val format: Format[UserRole] = Format(
    Reads { json =>
      json.validate[String].flatMap { s =>
        values.find(_.entryName == s.toLowerCase)
          .fold[JsResult[UserRole]](JsError(s"Invalid UserRole: $s"))(JsSuccess(_))
      }
    },
    Writes(r => JsString(r.entryName))
  )
}

// ─── SurgeonTier ──────────────────────────────────────────────────────────────

sealed trait SurgeonTier extends EnumEntry {
  override def entryName: String = toString.toLowerCase
}
object SurgeonTier extends Enum[SurgeonTier] {
  case object Essential extends SurgeonTier
  case object Gold      extends SurgeonTier
  case object Elite     extends SurgeonTier
  val values: IndexedSeq[SurgeonTier] = findValues
  override def withName(s: String): SurgeonTier = withNameInsensitive(s)

  implicit val format: Format[SurgeonTier] = Format(
    Reads { json =>
      json.validate[String].flatMap { s =>
        values.find(_.entryName == s.toLowerCase)
          .fold[JsResult[SurgeonTier]](JsError(s"Invalid SurgeonTier: $s"))(JsSuccess(_))
      }
    },
    Writes(r => JsString(r.entryName))
  )
}

// ─── ApplicationStatus ────────────────────────────────────────────────────────

sealed trait ApplicationStatus extends EnumEntry {
  override def entryName: String = toString match {
    case "MoreInfoRequired" => "more_info_required"
    case "InReview"         => "in_review"
    case other              => other.toLowerCase
  }
}
object ApplicationStatus extends Enum[ApplicationStatus] {
  case object Pending          extends ApplicationStatus
  case object InReview         extends ApplicationStatus
  case object Approved         extends ApplicationStatus
  case object Rejected         extends ApplicationStatus
  case object MoreInfoRequired extends ApplicationStatus
  val values: IndexedSeq[ApplicationStatus] = findValues
  override def withName(s: String): ApplicationStatus = s match {
    case "more_info_required" => MoreInfoRequired
    case "in_review"          => InReview
    case other                => withNameInsensitive(other)
  }

  implicit val format: Format[ApplicationStatus] = Format(
    Reads { json =>
      json.validate[String].flatMap { s =>
        values.find(_.entryName == s)
          .fold[JsResult[ApplicationStatus]](JsError(s"Invalid ApplicationStatus: $s"))(JsSuccess(_))
      }
    },
    Writes(r => JsString(r.entryName))
  )
}

// ─── ConsultationType ─────────────────────────────────────────────────────────

sealed trait ConsultationType extends EnumEntry {
  override def entryName: String = toString match {
    case "InClinic" => "in_clinic"
    case other      => other.toLowerCase
  }
}
object ConsultationType extends Enum[ConsultationType] {
  case object InClinic extends ConsultationType
  case object Virtual  extends ConsultationType
  val values: IndexedSeq[ConsultationType] = findValues
  override def withName(s: String): ConsultationType = s match {
    case "in_clinic" => InClinic
    case other       => withNameInsensitive(other)
  }

  implicit val format: Format[ConsultationType] = Format(
    Reads { json =>
      json.validate[String].flatMap { s =>
        values.find(_.entryName == s)
          .fold[JsResult[ConsultationType]](JsError(s"Invalid ConsultationType: $s"))(JsSuccess(_))
      }
    },
    Writes(r => JsString(r.entryName))
  )
}

// ─── BookingStatus ────────────────────────────────────────────────────────────

sealed trait BookingStatus extends EnumEntry {
  override def entryName: String = toString match {
    case "CancelledByPatient" => "cancelled_by_patient"
    case "CancelledBySurgeon" => "cancelled_by_surgeon"
    case other                => other.toLowerCase
  }
}
object BookingStatus extends Enum[BookingStatus] {
  case object Pending            extends BookingStatus
  case object Confirmed          extends BookingStatus
  case object Completed          extends BookingStatus
  case object CancelledByPatient extends BookingStatus
  case object CancelledBySurgeon extends BookingStatus
  val values: IndexedSeq[BookingStatus] = findValues
  override def withName(s: String): BookingStatus = s match {
    case "cancelled_by_patient" => CancelledByPatient
    case "cancelled_by_surgeon" => CancelledBySurgeon
    case other                  => withNameInsensitive(other)
  }

  implicit val format: Format[BookingStatus] = Format(
    Reads { json =>
      json.validate[String].flatMap { s =>
        values.find(_.entryName == s)
          .fold[JsResult[BookingStatus]](JsError(s"Invalid BookingStatus: $s"))(JsSuccess(_))
      }
    },
    Writes(r => JsString(r.entryName))
  )
}

// ─── EnquiryStatus ────────────────────────────────────────────────────────────

sealed trait EnquiryStatus extends EnumEntry {
  override def entryName: String = toString.toLowerCase
}
object EnquiryStatus extends Enum[EnquiryStatus] {
  case object Pending   extends EnquiryStatus
  case object Confirmed extends EnquiryStatus
  case object Declined  extends EnquiryStatus
  case object Completed extends EnquiryStatus
  case object Cancelled extends EnquiryStatus
  val values: IndexedSeq[EnquiryStatus] = findValues
  override def withName(s: String): EnquiryStatus = withNameInsensitive(s)

  implicit val format: Format[EnquiryStatus] = Format(
    Reads { json =>
      json.validate[String].flatMap { s =>
        values.find(_.entryName == s.toLowerCase)
          .fold[JsResult[EnquiryStatus]](JsError(s"Invalid EnquiryStatus: $s"))(JsSuccess(_))
      }
    },
    Writes(r => JsString(r.entryName))
  )
}

// ─── NotificationType ─────────────────────────────────────────────────────────

sealed trait NotificationType extends EnumEntry {
  override def entryName: String = toString match {
    case "BookingConfirmed"      => "booking_confirmed"
    case "BookingCancelled"      => "booking_cancelled"
    case "EnquiryReceived"       => "enquiry_received"
    case "EnquiryAccepted"       => "enquiry_accepted"
    case "EnquiryDeclined"       => "enquiry_declined"
    case "ApplicationApproved"   => "application_approved"
    case "ApplicationRejected"   => "application_rejected"
    case "ApplicationMoreInfo"   => "application_more_info"
    case "MessageReceived"       => "message_received"
    case "ReviewReceived"        => "review_received"
    case other                   => other.toLowerCase
  }
}
object NotificationType extends Enum[NotificationType] {
  case object BookingConfirmed    extends NotificationType
  case object BookingCancelled    extends NotificationType
  case object EnquiryReceived     extends NotificationType
  case object EnquiryAccepted     extends NotificationType
  case object EnquiryDeclined     extends NotificationType
  case object ApplicationApproved extends NotificationType
  case object ApplicationRejected extends NotificationType
  case object ApplicationMoreInfo extends NotificationType
  case object MessageReceived     extends NotificationType
  case object ReviewReceived      extends NotificationType
  val values: IndexedSeq[NotificationType] = findValues
  override def withName(s: String): NotificationType = values.find(_.entryName == s)
    .getOrElse(throw new NoSuchElementException(s"NotificationType: $s"))

  implicit val format: Format[NotificationType] = Format(
    Reads { json =>
      json.validate[String].flatMap { s =>
        values.find(_.entryName == s)
          .fold[JsResult[NotificationType]](JsError(s"Invalid NotificationType: $s"))(JsSuccess(_))
      }
    },
    Writes(r => JsString(r.entryName))
  )
}
