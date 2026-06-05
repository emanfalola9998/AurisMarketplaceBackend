// ─── app/co/auris/services/JwtService.scala ──────────────────────────────────
//
// Handles JWT access token + refresh token lifecycle.
//
// Access tokens:  short-lived (15 min), contain userId + role in claims
// Refresh tokens: long-lived (7 days), stored hashed in the database
//
// Depends on:
//   "com.github.jwt-scala" %% "jwt-play-json" % "10.0.1"

package co.auris.services

import co.auris.models.UserRole
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.Configuration
import play.api.libs.json.Json

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success}

@Singleton
class JwtService @Inject() (config: Configuration) {

  private val secret:         String = config.get[String]("auris.jwt.secret")
  private val algorithm              = JwtAlgorithm.HS256
  private val accessTokenTtl: Long   = config.get[Long]("auris.jwt.accessTokenTtl")
  private val refreshTokenTtl: Long  = config.get[Long]("auris.jwt.refreshTokenTtl")
  private val issuer:         String = config.get[String]("auris.jwt.issuer")

  // ─── Access token ──────────────────────────────────────────────────────────

  def generateAccessToken(userId: UUID, role: UserRole): String = {
    val now = Instant.now()
    val claim = JwtClaim(
      content    = Json.stringify(Json.obj("role" -> role.entryName)),
      subject    = Some(userId.toString),
      issuer     = Some(issuer),
      issuedAt   = Some(now.getEpochSecond),
      expiration = Some(now.getEpochSecond + accessTokenTtl)
    )
    JwtJson.encode(claim, secret, algorithm)
  }

  /** Returns Right(userId) on success, Left(error message) on failure. */
  def validateAccessToken(token: String): Either[String, UUID] =
    JwtJson.decode(token, secret, Seq(algorithm)) match {
      case Failure(ex) =>
        Left(s"Invalid token: ${ex.getMessage}")

      case Success(claim) =>
        claim.subject match {
          case None =>
            Left("Token missing subject claim")
          case Some(sub) =>
            try Right(UUID.fromString(sub))
            catch { case _: IllegalArgumentException => Left("Invalid subject UUID") }
        }
    }

  def accessTokenExpiresIn: Long = accessTokenTtl

  // ─── Refresh token ─────────────────────────────────────────────────────────
  // Refresh tokens are opaque random strings — not JWTs.
  // We store a BCrypt hash in the database and compare on refresh.

  def generateRefreshTokenRaw(): String = {
    val bytes = new Array[Byte](48)
    new java.security.SecureRandom().nextBytes(bytes)
    java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  }

  def refreshTokenExpiryInstant(): java.time.Instant =
    Instant.now().plusSeconds(refreshTokenTtl)
}