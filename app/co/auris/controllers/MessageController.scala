// ─── app/co/auris/controllers/MessageController.scala ────────────────────────

package co.auris.controllers

import co.auris.actions.JwtAuthAction
import co.auris.models._
import co.auris.repositories.{MessageRepository, UserRepository}
import play.api.libs.json._
import play.api.mvc._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageController @Inject() (
                                    cc:                ControllerComponents,
                                    authAction:        JwtAuthAction,
                                    messageRepository: MessageRepository,
                                    userRepository:    UserRepository
                                  )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // ─── GET /api/messages ────────────────────────────────────────────────────
  //
  // Returns one ConversationSummary per correspondent, newest conversation first.

  def inbox: Action[AnyContent] = authAction.async { implicit request =>
    val userId = request.userId

    messageRepository.listForUser(userId).map { messages =>
      def partnerOf(m: Message): UUID =
        if (m.senderId == userId) m.recipientId else m.senderId

      val conversations = messages
        .groupBy(partnerOf)
        .map { case (partnerId, msgs) =>
          // msgs is already newest-first since listForUser sorts by createdAt.desc
          val lastMessage = msgs.head
          val unreadCount = msgs.count(m => m.recipientId == userId && !m.isRead)
          ConversationSummary(partnerId, lastMessage, unreadCount)
        }
        .toList
        .sortBy(_.lastMessage.createdAt)(Ordering[java.time.OffsetDateTime].reverse)

      Ok(Json.obj("items" -> Json.toJson(conversations), "totalCount" -> conversations.length))
    }
  }

  // ─── POST /api/messages ───────────────────────────────────────────────────

  def send: Action[JsValue] = authAction(parse.json).async { implicit request =>
    val body = request.body
    val parsed = for {
      recipientIdStr <- (body \ "recipientId").asOpt[String]
      recipientId    <- scala.util.Try(UUID.fromString(recipientIdStr)).toOption
      messageBody    <- (body \ "body").asOpt[String].filter(_.trim.nonEmpty)
    } yield (recipientId, messageBody)

    parsed match {
      case None =>
        Future.successful(BadRequest(apiError("BAD_REQUEST", "recipientId and body are required.")))

      case Some((recipientId, _)) if recipientId == request.userId =>
        Future.successful(BadRequest(apiError("BAD_REQUEST", "You cannot message yourself.")))

      case Some((recipientId, messageBody)) =>
        userRepository.findById(recipientId).flatMap {
          case None =>
            Future.successful(NotFound(apiError("NOT_FOUND", "Recipient not found.")))
          case Some(_) =>
            messageRepository.send(request.userId, recipientId, messageBody).map { message =>
              Created(Json.toJson(message))
            }
        }
    }
  }

  // ─── GET /api/messages/:userId ────────────────────────────────────────────

  def thread(userId: UUID): Action[AnyContent] = authAction.async { implicit request =>
    messageRepository.thread(request.userId, userId).map { messages =>
      Ok(Json.obj("items" -> Json.toJson(messages), "totalCount" -> messages.length))
    }
  }

  // ─── PUT /api/messages/:id/read ───────────────────────────────────────────

  def markRead(id: UUID): Action[AnyContent] = authAction.async { implicit request =>
    messageRepository.markRead(id, request.userId).flatMap {
      case 0 =>
        messageRepository.findById(id).map {
          case None                                          => NotFound(apiError("NOT_FOUND", "Message not found."))
          case Some(m) if m.recipientId != request.userId    => Forbidden(apiError("FORBIDDEN", "This message was not sent to you."))
          case Some(_)                                       => Ok(Json.obj("message" -> "Already marked as read."))
        }
      case _ =>
        messageRepository.findById(id).map {
          case None    => NotFound(apiError("NOT_FOUND", "Message not found."))
          case Some(m) => Ok(Json.toJson(m))
        }
    }
  }

  private def apiError(code: String, message: String): JsValue =
    Json.toJson(ApiError(code, message))
}
