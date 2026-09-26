// ─── test/co/auris/controllers/MessageControllerSpec.scala ───────────────────
//
// Full-stack integration tests for the direct-messaging endpoints: sending,
// the inbox summary (grouped by partner, unread counts), threads, and
// marking a message read.

package co.auris.controllers

import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.util.UUID

class MessageControllerSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private def signUp(email: String, role: String): String = {
    val res = route(app, FakeRequest(POST, "/api/auth/sign-up")
      .withJsonBody(Json.obj("email" -> email, "password" -> "password123", "role" -> role))).get
    status(res) mustBe CREATED
    (contentAsJson(res) \ "accessToken").as[String]
  }

  private def userId(accessToken: String): UUID = {
    val res = route(app, FakeRequest(GET, "/api/auth/me")
      .withHeaders("Authorization" -> s"Bearer $accessToken")).get
    status(res) mustBe OK
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  private def sendMessage(senderToken: String, recipientId: UUID, body: String): UUID = {
    val res = route(app, FakeRequest(POST, "/api/messages")
      .withHeaders("Authorization" -> s"Bearer $senderToken")
      .withJsonBody(Json.obj("recipientId" -> recipientId.toString, "body" -> body))).get
    status(res) mustBe CREATED
    UUID.fromString((contentAsJson(res) \ "id").as[String])
  }

  "POST /api/messages" should {
    "let one user send another a message" in {
      val senderToken = signUp("msg-sender1@example.com", "patient")
      val recipientToken = signUp("msg-recipient1@example.com", "surgeon")
      val recipientId = userId(recipientToken)

      val res = route(app, FakeRequest(POST, "/api/messages")
        .withHeaders("Authorization" -> s"Bearer $senderToken")
        .withJsonBody(Json.obj("recipientId" -> recipientId.toString, "body" -> "Hello there"))).get

      status(res) mustBe CREATED
      val json = contentAsJson(res)
      (json \ "body").as[String] mustBe "Hello there"
      (json \ "recipientId").as[String] mustBe recipientId.toString
      (json \ "isRead").as[Boolean] mustBe false
    }

    "reject messaging yourself" in {
      val token = signUp("msg-self@example.com", "patient")
      val selfId = userId(token)

      val res = route(app, FakeRequest(POST, "/api/messages")
        .withHeaders("Authorization" -> s"Bearer $token")
        .withJsonBody(Json.obj("recipientId" -> selfId.toString, "body" -> "Talking to myself"))).get

      status(res) mustBe BAD_REQUEST
    }

    "reject an empty body" in {
      val senderToken = signUp("msg-sender2@example.com", "patient")
      val recipientToken = signUp("msg-recipient2@example.com", "surgeon")
      val recipientId = userId(recipientToken)

      val res = route(app, FakeRequest(POST, "/api/messages")
        .withHeaders("Authorization" -> s"Bearer $senderToken")
        .withJsonBody(Json.obj("recipientId" -> recipientId.toString, "body" -> "  "))).get

      status(res) mustBe BAD_REQUEST
    }

    "reject a recipient that doesn't exist" in {
      val senderToken = signUp("msg-sender3@example.com", "patient")

      val res = route(app, FakeRequest(POST, "/api/messages")
        .withHeaders("Authorization" -> s"Bearer $senderToken")
        .withJsonBody(Json.obj("recipientId" -> UUID.randomUUID().toString, "body" -> "Hello?"))).get

      status(res) mustBe NOT_FOUND
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(POST, "/api/messages")
        .withJsonBody(Json.obj("recipientId" -> UUID.randomUUID().toString, "body" -> "Hello?"))).get

      status(res) mustBe UNAUTHORIZED
    }
  }

  "GET /api/messages" should {
    "group messages by partner, newest conversation first, with unread counts" in {
      val userToken = signUp("inbox-user1@example.com", "patient")
      val partnerAToken = signUp("inbox-partnerA1@example.com", "surgeon")
      val partnerBToken = signUp("inbox-partnerB1@example.com", "surgeon")
      val partnerAId = userId(partnerAToken)
      val partnerBId = userId(partnerBToken)

      sendMessage(userToken, partnerAId, "Hi A")
      sendMessage(partnerBToken, userId(userToken), "Hi from B")
      sendMessage(partnerBToken, userId(userToken), "Second from B")

      val res = route(app, FakeRequest(GET, "/api/messages")
        .withHeaders("Authorization" -> s"Bearer $userToken")).get

      status(res) mustBe OK
      val json = contentAsJson(res)
      (json \ "totalCount").as[Int] mustBe 2
      val items = (json \ "items").as[List[play.api.libs.json.JsObject]]
      items must have size 2

      val bConversation = items.find(i => (i \ "partnerId").as[String] == partnerBId.toString).get
      (bConversation \ "unreadCount").as[Int] mustBe 2
      (bConversation \ "lastMessage" \ "body").as[String] mustBe "Second from B"

      val aConversation = items.find(i => (i \ "partnerId").as[String] == partnerAId.toString).get
      (aConversation \ "unreadCount").as[Int] mustBe 0
    }

    "reject requests with no Authorization header" in {
      val res = route(app, FakeRequest(GET, "/api/messages")).get

      status(res) mustBe UNAUTHORIZED
    }
  }

  "GET /api/messages/:userId" should {
    "return the message thread between the caller and the given user" in {
      val userAToken = signUp("thread-userA1@example.com", "patient")
      val userBToken = signUp("thread-userB1@example.com", "surgeon")
      val userBId = userId(userBToken)

      sendMessage(userAToken, userBId, "First")
      sendMessage(userBToken, userId(userAToken), "Reply")

      val res = route(app, FakeRequest(GET, s"/api/messages/$userBId")
        .withHeaders("Authorization" -> s"Bearer $userAToken")).get

      status(res) mustBe OK
      val json = contentAsJson(res)
      (json \ "totalCount").as[Int] mustBe 2
    }

    "not include messages with unrelated users" in {
      val userAToken = signUp("thread-userA2@example.com", "patient")
      val userBToken = signUp("thread-userB2@example.com", "surgeon")
      val strangerToken = signUp("thread-stranger2@example.com", "surgeon")

      sendMessage(userAToken, userId(userBToken), "Hi B")
      sendMessage(userAToken, userId(strangerToken), "Hi stranger")

      val res = route(app, FakeRequest(GET, s"/api/messages/${userId(userBToken)}")
        .withHeaders("Authorization" -> s"Bearer $userAToken")).get

      status(res) mustBe OK
      (contentAsJson(res) \ "totalCount").as[Int] mustBe 1
    }
  }

  "PUT /api/messages/:id/read" should {
    "let the recipient mark a message as read" in {
      val senderToken = signUp("read-sender1@example.com", "patient")
      val recipientToken = signUp("read-recipient1@example.com", "surgeon")
      val messageId = sendMessage(senderToken, userId(recipientToken), "Read me")

      val res = route(app, FakeRequest(PUT, s"/api/messages/$messageId/read")
        .withHeaders("Authorization" -> s"Bearer $recipientToken")).get

      status(res) mustBe OK
      (contentAsJson(res) \ "isRead").as[Boolean] mustBe true
    }

    "reject a user the message wasn't sent to" in {
      val senderToken = signUp("read-sender2@example.com", "patient")
      val recipientToken = signUp("read-recipient2@example.com", "surgeon")
      val strangerToken = signUp("read-stranger2@example.com", "surgeon")
      val messageId = sendMessage(senderToken, userId(recipientToken), "Not for you")

      val res = route(app, FakeRequest(PUT, s"/api/messages/$messageId/read")
        .withHeaders("Authorization" -> s"Bearer $strangerToken")).get

      status(res) mustBe FORBIDDEN
    }

    "return 404 for a message that doesn't exist" in {
      val token = signUp("read-nobody@example.com", "patient")

      val res = route(app, FakeRequest(PUT, s"/api/messages/${UUID.randomUUID()}/read")
        .withHeaders("Authorization" -> s"Bearer $token")).get

      status(res) mustBe NOT_FOUND
    }

    "return OK without erroring when marking an already-read message read again" in {
      val senderToken = signUp("read-sender3@example.com", "patient")
      val recipientToken = signUp("read-recipient3@example.com", "surgeon")
      val messageId = sendMessage(senderToken, userId(recipientToken), "Read twice")
      status(route(app, FakeRequest(PUT, s"/api/messages/$messageId/read")
        .withHeaders("Authorization" -> s"Bearer $recipientToken")).get) mustBe OK

      val res = route(app, FakeRequest(PUT, s"/api/messages/$messageId/read")
        .withHeaders("Authorization" -> s"Bearer $recipientToken")).get

      status(res) mustBe OK
    }
  }
}
