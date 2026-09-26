// ─── app/co/auris/repositories/UserRepository.scala ─────────────────────────
//
// All database operations for the users, refresh_tokens,
// email_verifications, and password_resets tables.
//
// Returns Future[T] — controllers and services never touch Slick directly.
// All DB calls run on the blocking-dispatcher defined in application.conf.

package co.auris.repositories

import co.auris.db.AurisPostgresProfile.api._
import co.auris.db.{EmailVerifications, PasswordResets, RefreshTokens, Users}
import co.auris.models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserRepository @Inject() (
                                 protected val dbConfigProvider: DatabaseConfigProvider
                               )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  // ─── Users ─────────────────────────────────────────────────────────────────

  def findById(id: UUID): Future[Option[User]] =
    db.run(Users.filter(_.id === id).result.headOption)

  def findByEmail(email: String): Future[Option[User]] =
    db.run(Users.filter(_.email === email.toLowerCase.trim).result.headOption)

  def create(
              email:        String,
              passwordHash: String,
              role:         UserRole
            ): Future[User] = {
    val now  = OffsetDateTime.now(ZoneOffset.UTC)
    val user = User(
      id              = UUID.randomUUID(),
      email           = email.toLowerCase.trim,
      passwordHash    = passwordHash,
      role            = role,
      isActive        = true,
      isEmailVerified = false,
      createdAt       = now,
      updatedAt       = now
    )
    db.run((Users += user).map(_ => user))
  }

  def updatePasswordHash(userId: UUID, newHash: String): Future[Int] =
    db.run(
      Users
        .filter(_.id === userId)
        .map(u => (u.passwordHash, u.updatedAt))
        .update((newHash, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  def markEmailVerified(userId: UUID): Future[Int] =
    db.run(
      Users
        .filter(_.id === userId)
        .map(u => (u.isEmailVerified, u.updatedAt))
        .update((true, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  def setActive(userId: UUID, active: Boolean): Future[Int] =
    db.run(
      Users
        .filter(_.id === userId)
        .map(u => (u.isActive, u.updatedAt))
        .update((active, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  /** Account deletion: deactivates the user and frees up their real email
    * (unique constraint) behind a placeholder so it can be reused for a
    * future sign-up. The row itself is kept — bookings/reviews/enquiries
    * reference it with no ON DELETE CASCADE, so a hard delete would fail
    * with a foreign key violation the moment any history exists. */
  def deactivateAndAnonymizeEmail(userId: UUID, anonymizedEmail: String): Future[Int] =
    db.run(
      Users
        .filter(_.id === userId)
        .map(u => (u.isActive, u.email, u.updatedAt))
        .update((false, anonymizedEmail, OffsetDateTime.now(ZoneOffset.UTC)))
    )

  def emailExists(email: String): Future[Boolean] =
    db.run(
      Users.filter(_.email === email.toLowerCase.trim).exists.result
    )

  // ─── Refresh tokens ────────────────────────────────────────────────────────

  def createRefreshToken(
                          userId:    UUID,
                          tokenHash: String,
                          expiresAt: Instant,
                          userAgent: Option[String] = None,
                          ipAddress: Option[String] = None
                        ): Future[RefreshToken] = {
    val now   = OffsetDateTime.now(ZoneOffset.UTC)
    val token = RefreshToken(
      id        = UUID.randomUUID(),
      userId    = userId,
      tokenHash = tokenHash,
      expiresAt = expiresAt.atOffset(ZoneOffset.UTC),
      createdAt = now,
      revokedAt = None,
      userAgent = userAgent,
      ipAddress = ipAddress
    )
    db.run((RefreshTokens += token).map(_ => token))
  }

  def findRefreshToken(tokenHash: String): Future[Option[RefreshToken]] =
    db.run(
      RefreshTokens
        .filter(t =>
          t.tokenHash === tokenHash &&
            t.revokedAt.isEmpty &&
            t.expiresAt > OffsetDateTime.now(ZoneOffset.UTC)
        )
        .result.headOption
    )

  def revokeRefreshToken(tokenHash: String): Future[Int] =
    db.run(
      RefreshTokens
        .filter(_.tokenHash === tokenHash)
        .map(_.revokedAt)
        .update(Some(OffsetDateTime.now(ZoneOffset.UTC)))
    )

  def revokeAllRefreshTokensForUser(userId: UUID): Future[Int] =
    db.run(
      RefreshTokens
        .filter(t => t.userId === userId && t.revokedAt.isEmpty)
        .map(_.revokedAt)
        .update(Some(OffsetDateTime.now(ZoneOffset.UTC)))
    )

  // ─── Email verification ────────────────────────────────────────────────────

  def createEmailVerification(userId: UUID, token: String, ttlSeconds: Long): Future[EmailVerification] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val ev  = EmailVerification(
      id        = UUID.randomUUID(),
      userId    = userId,
      token     = token,
      expiresAt = now.plusSeconds(ttlSeconds),
      usedAt    = None,
      createdAt = now
    )
    db.run((EmailVerifications += ev).map(_ => ev))
  }

  def findEmailVerification(token: String): Future[Option[EmailVerification]] =
    db.run(
      EmailVerifications
        .filter(e =>
          e.token === token &&
            e.usedAt.isEmpty &&
            e.expiresAt > OffsetDateTime.now(ZoneOffset.UTC)
        )
        .result.headOption
    )

  def markEmailVerificationUsed(id: UUID): Future[Int] =
    db.run(
      EmailVerifications
        .filter(_.id === id)
        .map(_.usedAt)
        .update(Some(OffsetDateTime.now(ZoneOffset.UTC)))
    )

  // ─── Password reset ────────────────────────────────────────────────────────

  def createPasswordReset(userId: UUID, tokenHash: String, ttlSeconds: Long): Future[PasswordReset] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val pr  = PasswordReset(
      id        = UUID.randomUUID(),
      userId    = userId,
      tokenHash = tokenHash,
      expiresAt = now.plusSeconds(ttlSeconds),
      usedAt    = None,
      createdAt = now
    )
    db.run((PasswordResets += pr).map(_ => pr))
  }

  def findPasswordReset(tokenHash: String): Future[Option[PasswordReset]] =
    db.run(
      PasswordResets
        .filter(r =>
          r.tokenHash === tokenHash &&
            r.usedAt.isEmpty &&
            r.expiresAt > OffsetDateTime.now(ZoneOffset.UTC)
        )
        .result.headOption
    )

  def markPasswordResetUsed(id: UUID): Future[Int] =
    db.run(
      PasswordResets
        .filter(_.id === id)
        .map(_.usedAt)
        .update(Some(OffsetDateTime.now(ZoneOffset.UTC)))
    )

  // ─── Transactional helpers ─────────────────────────────────────────────────

  /** Creates a user + immediately creates a refresh token in a single transaction. */
  def createUserWithRefreshToken(
                                  email:        String,
                                  passwordHash: String,
                                  role:         UserRole,
                                  tokenHash:    String,
                                  expiresAt:    Instant,
                                  userAgent:    Option[String],
                                  ipAddress:    Option[String]
                                ): Future[(User, RefreshToken)] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val userId = UUID.randomUUID()

    val user = User(
      id              = userId,
      email           = email.toLowerCase.trim,
      passwordHash    = passwordHash,
      role            = role,
      isActive        = true,
      isEmailVerified = false,
      createdAt       = now,
      updatedAt       = now
    )

    val refreshToken = RefreshToken(
      id        = UUID.randomUUID(),
      userId    = userId,
      tokenHash = tokenHash,
      expiresAt = expiresAt.atOffset(ZoneOffset.UTC),
      createdAt = now,
      revokedAt = None,
      userAgent = userAgent,
      ipAddress = ipAddress
    )

    val action = for {
      _ <- Users         += user
      _ <- RefreshTokens += refreshToken
    } yield (user, refreshToken)

    db.run(action.transactionally)
  }
}