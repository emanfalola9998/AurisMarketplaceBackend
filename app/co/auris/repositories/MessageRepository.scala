// ─── app/co/auris/repositories/MessageRepository.scala ───────────────────────

package co.auris.repositories

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.Messages
import co.auris.models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageRepository @Inject() (
                                    protected val dbConfigProvider: DatabaseConfigProvider
                                  )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  def send(senderId: UUID, recipientId: UUID, body: String): Future[Message] = {
    val message = Message(
      id          = UUID.randomUUID(),
      senderId    = senderId,
      recipientId = recipientId,
      body        = body,
      isRead      = false,
      readAt      = None,
      createdAt   = OffsetDateTime.now(ZoneOffset.UTC)
    )
    db.run((Messages += message).map(_ => message))
  }

  def findById(id: UUID): Future[Option[Message]] =
    db.run(Messages.filter(_.id === id).result.headOption)

  /** All messages involving this user, newest first — used to build the inbox and thread views. */
  def listForUser(userId: UUID): Future[List[Message]] =
    db.run(
      Messages
        .filter(m => m.senderId === userId || m.recipientId === userId)
        .sortBy(_.createdAt.desc)
        .result
    ).map(_.toList)

  def thread(userId: UUID, otherUserId: UUID): Future[List[Message]] =
    db.run(
      Messages
        .filter { m =>
          (m.senderId === userId && m.recipientId === otherUserId) ||
          (m.senderId === otherUserId && m.recipientId === userId)
        }
        .sortBy(_.createdAt.asc)
        .result
    ).map(_.toList)

  def markRead(id: UUID, recipientId: UUID): Future[Int] =
    db.run(
      Messages
        .filter(m => m.id === id && m.recipientId === recipientId && m.isRead === false)
        .map(m => (m.isRead, m.readAt))
        .update((true, Some(OffsetDateTime.now(ZoneOffset.UTC))))
    )
}
