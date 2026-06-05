// ─── app/co/auris/db/AurisPostgresProfile.scala ──────────────────────────────
//
// Custom Slick profile that extends slick-pg.
// Adds support for:
//   - UUID columns (maps to java.util.UUID)
//   - PostgreSQL enums (mapped to Scala sealed traits)
//   - Text arrays (TEXT[])
//   - JSONB columns (mapped to play.api.libs.json.JsValue)
//   - INET (mapped to String for now)
//   - Date/time: java.time types via slick-pg's date support

package co.auris.db

import com.github.tminglei.slickpg._
import slick.basic.Capability

trait AurisPostgresProfile
  extends ExPostgresProfile
    with PgArraySupport
    with PgDate2Support
    with PgPlayJsonSupport
    with PgEnumSupport {

  def pgjson = "jsonb"

  // Add back capabilities removed by slick-pg
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate

  override val api: AurisAPI = new AurisAPI {}

  trait AurisAPI
    extends ExtPostgresAPI
      with ArrayImplicits
      with Date2DateTimeImplicits[String]
      with PlayJsonImplicits
      with SimpleArrayPlainImplicits {

    // ── Enum mappings ─────────────────────────────────────────────────────
    // Each enum needs an explicit columnType so Slick knows how to
    // read/write it from Postgres.

    implicit val userRoleTypeMapper: BaseColumnType[co.auris.models.UserRole] =
      createEnumJdbcType("user_role", _.entryName, co.auris.models.UserRole.withName, quoteName = false)

    implicit val surgeonTierTypeMapper: BaseColumnType[co.auris.models.SurgeonTier] =
      createEnumJdbcType("surgeon_tier", _.entryName, co.auris.models.SurgeonTier.withName, quoteName = false)

    implicit val applicationStatusTypeMapper: BaseColumnType[co.auris.models.ApplicationStatus] =
      createEnumJdbcType("application_status", _.entryName, co.auris.models.ApplicationStatus.withName, quoteName = false)

    implicit val consultationTypeMapper: BaseColumnType[co.auris.models.ConsultationType] =
      createEnumJdbcType("consultation_type", _.entryName, co.auris.models.ConsultationType.withName, quoteName = false)

    implicit val bookingStatusTypeMapper: BaseColumnType[co.auris.models.BookingStatus] =
      createEnumJdbcType("booking_status", _.entryName, co.auris.models.BookingStatus.withName, quoteName = false)

    implicit val enquiryStatusTypeMapper: BaseColumnType[co.auris.models.EnquiryStatus] =
      createEnumJdbcType("enquiry_status", _.entryName, co.auris.models.EnquiryStatus.withName, quoteName = false)

    implicit val notificationTypeMapper: BaseColumnType[co.auris.models.NotificationType] =
      createEnumJdbcType("notification_type", _.entryName, co.auris.models.NotificationType.withName, quoteName = false)

    // ── Column type column shortcuts ──────────────────────────────────────
    // Used in table definitions as: def role = column[UserRole]("role")
  }
}

object AurisPostgresProfile extends AurisPostgresProfile