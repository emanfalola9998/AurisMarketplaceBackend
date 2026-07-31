// ─── app/co/auris/services/AuthService.scala ─────────────────────────────────
//
// Business logic for all authentication flows:
//
//   signIn          → verify credentials, issue access + refresh tokens
//   signUp          → create user + profile row, issue tokens
//   refresh         → rotate refresh token, issue new access token
//   signOut         → revoke refresh token
//   signOutAll      → revoke all refresh tokens for the user
//   verifyEmail     → consume email verification token
//   forgotPassword  → generate and email a reset token
//   resetPassword   → consume reset token, update password hash
//
// AuthService never touches HTTP — all errors are returned as Either[AuthError, T].
// The controller handles mapping these to HTTP responses.

package co.auris.services

import co.auris.models._
import co.auris.repositories.{BookingRepository, PatientRepository, SurgeonRepository, UserRepository}
import org.mindrot.jbcrypt.BCrypt
import play.api.Configuration

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// ─── Error ADT ────────────────────────────────────────────────────────────────

sealed trait AuthError
object AuthError {
  case object InvalidCredentials       extends AuthError
  case object EmailAlreadyExists       extends AuthError
  case object UserNotFound             extends AuthError
  case object UserInactive             extends AuthError
  case object InvalidToken             extends AuthError
  case object TokenExpired             extends AuthError
  case object RoleNotSupported         extends AuthError
  case class  Unexpected(msg: String)  extends AuthError
}

// ─── Token pair ───────────────────────────────────────────────────────────────

case class TokenPair(
                      accessToken:  String,
                      refreshToken: String,
                      expiresIn:    Long
                    )

// ─── AuthService ──────────────────────────────────────────────────────────────

@Singleton
class AuthService @Inject() (
                              userRepository:      UserRepository,
                              patientRepository:   PatientRepository,
                              surgeonRepository:   SurgeonRepository,
                              bookingRepository:   BookingRepository,
                              jwtService:          JwtService,
                              notificationService: NotificationService,
                              config:              Configuration
                            )(implicit ec: ExecutionContext) {

  private val bcryptRounds: Int = config.get[Int]("auris.bcrypt.rounds")

  // ─── Sign in ────────────────────────────────────────────────────────────────

  def signIn(
              email:     String,
              password:  String,
              userAgent: Option[String] = None,
              ipAddress: Option[String] = None
            ): Future[Either[AuthError, (User, TokenPair)]] =
    userRepository.findByEmail(email).flatMap {
      case None =>
        // Hash a dummy value to prevent timing attacks
        BCrypt.hashpw("dummy", BCrypt.gensalt(bcryptRounds))
        Future.successful(Left(AuthError.InvalidCredentials))

      case Some(user) if !user.isActive =>
        Future.successful(Left(AuthError.UserInactive))

      case Some(user) if !BCrypt.checkpw(password, user.passwordHash) =>
        Future.successful(Left(AuthError.InvalidCredentials))

      case Some(user) =>
        issueTokens(user, userAgent, ipAddress).map(pair => Right((user, pair)))
    }

  // ─── Sign up ────────────────────────────────────────────────────────────────

  def signUp(
              email:     String,
              password:  String,
              role:      UserRole,
              userAgent: Option[String] = None,
              ipAddress: Option[String] = None
            ): Future[Either[AuthError, (User, TokenPair)]] =
    userRepository.emailExists(email).flatMap {
      case true =>
        Future.successful(Left(AuthError.EmailAlreadyExists))

      case false =>
        val hash         = BCrypt.hashpw(password, BCrypt.gensalt(bcryptRounds))
        val rawRefresh   = jwtService.generateRefreshTokenRaw()
        val refreshHash  = hashToken(rawRefresh)
        val expiresAt    = jwtService.refreshTokenExpiryInstant()

        userRepository
          .createUserWithRefreshToken(email, hash, role, refreshHash, expiresAt, userAgent, ipAddress)
          .flatMap { case (user, _) =>
            // Create the role-specific profile row
            val profileFuture: Future[Unit] = role match {
              case UserRole.Patient => patientRepository.createProfile(user.id, email).map(_ => ())
              case UserRole.Surgeon => surgeonRepository.createProfile(user.id, email).map(_ => ())
              case UserRole.Admin   => Future.successful(())
            }

            profileFuture.flatMap { _ =>
              val verificationToken = jwtService.generateRefreshTokenRaw()
              userRepository.createEmailVerification(user.id, verificationToken, 86400L)
                .flatMap(_ => notificationService.sendEmailVerification(user.email, verificationToken))
                .recover { case _ => () }
                .map { _ =>
                  val accessToken = jwtService.generateAccessToken(user.id, user.role)
                  Right((user, TokenPair(accessToken, rawRefresh, jwtService.accessTokenExpiresIn)))
                }
            }
          }
          .recover { case ex =>
            Left(AuthError.Unexpected(ex.getMessage))
          }
    }

  // ─── Refresh tokens ─────────────────────────────────────────────────────────

  def refresh(
               rawRefreshToken: String,
               userAgent:       Option[String] = None,
               ipAddress:       Option[String] = None
             ): Future[Either[AuthError, (User, TokenPair)]] = {
    // We find the token by iterating candidate records.
    // In production with high traffic, consider using the first 8 chars as a
    // lookup key stored in plaintext alongside the hash.
    userRepository.findRefreshToken(hashToken(rawRefreshToken)).flatMap {
      case None =>
        Future.successful(Left(AuthError.InvalidToken))

      case Some(storedToken) =>
        userRepository.findById(storedToken.userId).flatMap {
          case None       => Future.successful(Left(AuthError.UserNotFound))
          case Some(user) if !user.isActive => Future.successful(Left(AuthError.UserInactive))
          case Some(user) =>
            // Revoke the old token then issue a new pair (rotation)
            userRepository.revokeRefreshToken(storedToken.tokenHash).flatMap { _ =>
              issueTokens(user, userAgent, ipAddress).map(pair => Right((user, pair)))
            }
        }
    }
  }

  // ─── Sign out ────────────────────────────────────────────────────────────────

  def signOut(rawRefreshToken: String): Future[Unit] =
    userRepository.revokeRefreshToken(hashToken(rawRefreshToken)).map(_ => ())

  def signOutAll(userId: UUID): Future[Unit] =
    userRepository.revokeAllRefreshTokensForUser(userId).map(_ => ())

  // ─── Account deletion ──────────────────────────────────────────────────────
  //
  // Patient-only for now (the only role with a "Delete account" button).
  // This is a soft delete: bookings/enquiries/reviews reference the profile
  // row with no ON DELETE CASCADE, so a hard delete of the user or patient
  // row would fail with a foreign key violation the moment any booking
  // history exists. Instead: verify the password, cancel anything still
  // scheduled, wipe personal fields, deactivate the account, and free up
  // the email behind a placeholder so it can be reused for a fresh sign-up.

  def deleteAccount(userId: UUID, password: String): Future[Either[AuthError, Unit]] =
    userRepository.findById(userId).flatMap {
      case None =>
        Future.successful(Left(AuthError.UserNotFound))

      case Some(user) if user.role != UserRole.Patient =>
        Future.successful(Left(AuthError.RoleNotSupported))

      case Some(user) if !BCrypt.checkpw(password, user.passwordHash) =>
        Future.successful(Left(AuthError.InvalidCredentials))

      case Some(user) =>
        patientRepository.findByUserId(user.id).flatMap {
          case None =>
            Future.successful(Left(AuthError.UserNotFound))

          case Some(patient) =>
            val anonymizedEmail = s"deleted-${user.id}@deleted.auris.co"
            for {
              _ <- bookingRepository.cancelAllFutureActiveForPatient(patient.id)
              _ <- userRepository.revokeAllRefreshTokensForUser(user.id)
              _ <- patientRepository.anonymize(patient.id)
              _ <- userRepository.deactivateAndAnonymizeEmail(user.id, anonymizedEmail)
            } yield Right(())
        }
    }

  // ─── Email verification ──────────────────────────────────────────────────────

  def verifyEmail(token: String): Future[Either[AuthError, Unit]] =
    userRepository.findEmailVerification(token).flatMap {
      case None => Future.successful(Left(AuthError.InvalidToken))
      case Some(ev) =>
        for {
          _ <- userRepository.markEmailVerificationUsed(ev.id)
          _ <- userRepository.markEmailVerified(ev.userId)
        } yield Right(())
    }

  // ─── Password reset ──────────────────────────────────────────────────────────

  def initiatePasswordReset(email: String): Future[Option[String]] =
    userRepository.findByEmail(email).flatMap {
      case None => Future.successful(None)
      case Some(user) =>
        val rawToken = jwtService.generateRefreshTokenRaw() // reuse the random generator
        val tokenHash = hashToken(rawToken)
        userRepository.createPasswordReset(user.id, tokenHash, 3600L).flatMap { _ =>
          notificationService.sendPasswordReset(user.email, rawToken)
            .recover { case _ => () }
            .map(_ => Some(rawToken))
        }
    }

  def completePasswordReset(
                             rawToken:    String,
                             newPassword: String
                           ): Future[Either[AuthError, Unit]] =
    userRepository.findPasswordReset(hashToken(rawToken)).flatMap {
      case None => Future.successful(Left(AuthError.InvalidToken))
      case Some(pr) =>
        val newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(bcryptRounds))
        for {
          _ <- userRepository.markPasswordResetUsed(pr.id)
          _ <- userRepository.updatePasswordHash(pr.userId, newHash)
          _ <- userRepository.revokeAllRefreshTokensForUser(pr.userId)
        } yield Right(())
    }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  private def issueTokens(
                           user:      User,
                           userAgent: Option[String],
                           ipAddress: Option[String]
                         ): Future[TokenPair] = {
    val rawRefresh  = jwtService.generateRefreshTokenRaw()
    val refreshHash = hashToken(rawRefresh)
    val expiresAt   = jwtService.refreshTokenExpiryInstant()
    val accessToken = jwtService.generateAccessToken(user.id, user.role)

    userRepository
      .createRefreshToken(user.id, refreshHash, expiresAt, userAgent, ipAddress)
      .map(_ => TokenPair(accessToken, rawRefresh, jwtService.accessTokenExpiresIn))
  }

  // SHA-256 hash for refresh token storage — BCrypt is overkill for a random token.
  private def hashToken(raw: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    java.util.Base64.getUrlEncoder.withoutPadding()
      .encodeToString(digest.digest(raw.getBytes("UTF-8")))
  }
}