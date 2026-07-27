// ─── app/co/auris/repositories/AuditLogRepository.scala ──────────────────────

package co.auris.repositories

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.AuditLog
import co.auris.models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue
import slick.jdbc.JdbcProfile

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditLogRepository @Inject() (
                                     protected val dbConfigProvider: DatabaseConfigProvider
                                   )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  def log(
           actorId:    UUID,
           action:     String,
           targetType: String,
           targetId:   UUID,
           metadata:   Option[JsValue] = None,
           ipAddress:  Option[String]  = None
         ): Future[AuditLogEntry] = {
    val entry = AuditLogEntry(
      id         = UUID.randomUUID(),
      actorId    = actorId,
      action     = action,
      targetType = targetType,
      targetId   = targetId,
      metadata   = metadata,
      ipAddress  = ipAddress,
      createdAt  = OffsetDateTime.now(ZoneOffset.UTC)
    )
    db.run((AuditLog += entry).map(_ => entry))
  }

  def listForTarget(targetType: String, targetId: UUID): Future[List[AuditLogEntry]] =
    db.run(
      AuditLog
        .filter(a => a.targetType === targetType && a.targetId === targetId)
        .sortBy(_.createdAt.desc)
        .result
    ).map(_.toList)
}
