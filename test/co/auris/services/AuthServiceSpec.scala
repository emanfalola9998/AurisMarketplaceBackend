// ─── test/co/auris/services/AuthServiceSpec.scala ─────────────────────────────

package co.auris.services

import co.auris.models._
import co.auris.repositories.{BookingRepository, PatientRepository, SurgeonRepository, UserRepository}
import co.auris.support.Fixtures
import org.mindrot.jbcrypt.BCrypt
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthServiceSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with ArgumentMatchersSugar
  with BeforeAndAfterEach {

  private var userRepository:    UserRepository    = _
  private var patientRepository: PatientRepository = _
  private var surgeonRepository: SurgeonRepository = _
  private var bookingRepository: BookingRepository = _
  private var jwtService:        JwtService        = _
  private var notificationService: NotificationService = _
  private var service: AuthService = _

  // Bcrypt at 4 rounds instead of the app's 12 — same code path, far faster tests.
  private val testConfig = Configuration("auris.bcrypt.rounds" -> 4)

  override def beforeEach(): Unit = {
    userRepository       = mock[UserRepository]
    patientRepository    = mock[PatientRepository]
    surgeonRepository    = mock[SurgeonRepository]
    bookingRepository    = mock[BookingRepository]
    jwtService           = mock[JwtService]
    notificationService  = mock[NotificationService]
    service = new AuthService(
      userRepository, patientRepository, surgeonRepository, bookingRepository,
      jwtService, notificationService, testConfig
    )

    // Common stubs used by nearly every path that issues tokens.
    when(jwtService.generateRefreshTokenRaw()).thenReturn("raw-refresh-token")
    when(jwtService.refreshTokenExpiryInstant()).thenReturn(Instant.now().plusSeconds(604800))
    when(jwtService.generateAccessToken(any[UUID], any[UserRole])).thenReturn("access-token")
    when(jwtService.accessTokenExpiresIn).thenReturn(900L)
    when(userRepository.createRefreshToken(any[UUID], any[String], any[Instant], any[Option[String]], any[Option[String]]))
      .thenReturn(Future.successful(Fixtures.refreshToken(UUID.randomUUID())))
    ()
  }

  "signIn" should {
    "succeed with correct credentials" in {
      val plainPassword = "correct-password"
      val hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(4))
      val user = Fixtures.user(passwordHash = hash)
      when(userRepository.findByEmail("patient@example.com")).thenReturn(Future.successful(Some(user)))

      val result = service.signIn("patient@example.com", plainPassword).futureValue

      result mustBe a[Right[_, _]]
      result.toOption.get._1 mustBe user
      result.toOption.get._2.accessToken mustBe "access-token"
    }

    "reject an unknown email without revealing that it doesn't exist" in {
      when(userRepository.findByEmail("ghost@example.com")).thenReturn(Future.successful(None))

      val result = service.signIn("ghost@example.com", "whatever").futureValue

      result mustBe Left(AuthError.InvalidCredentials)
    }

    "reject the wrong password" in {
      val hash = BCrypt.hashpw("correct-password", BCrypt.gensalt(4))
      val user = Fixtures.user(passwordHash = hash)
      when(userRepository.findByEmail(user.email)).thenReturn(Future.successful(Some(user)))

      val result = service.signIn(user.email, "wrong-password").futureValue

      result mustBe Left(AuthError.InvalidCredentials)
    }

    "reject an inactive user even with the correct password" in {
      val plainPassword = "correct-password"
      val hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(4))
      val user = Fixtures.user(passwordHash = hash, isActive = false)
      when(userRepository.findByEmail(user.email)).thenReturn(Future.successful(Some(user)))

      val result = service.signIn(user.email, plainPassword).futureValue

      result mustBe Left(AuthError.UserInactive)
    }
  }

  "signUp" should {
    "reject an email that's already taken" in {
      when(userRepository.emailExists("taken@example.com")).thenReturn(Future.successful(true))

      val result = service.signUp("taken@example.com", "password123", UserRole.Patient).futureValue

      result mustBe Left(AuthError.EmailAlreadyExists)
      verify(userRepository, never).createUserWithRefreshToken(
        any[String], any[String], any[UserRole], any[String], any[Instant], any[Option[String]], any[Option[String]]
      )
    }

    "create a patient profile when signing up as a patient" in {
      val newUser = Fixtures.user(role = UserRole.Patient)
      when(userRepository.emailExists(newUser.email)).thenReturn(Future.successful(false))
      when(userRepository.createUserWithRefreshToken(eqTo(newUser.email), any[String], eqTo(UserRole.Patient), any[String], any[Instant], any[Option[String]], any[Option[String]]))
        .thenReturn(Future.successful((newUser, Fixtures.refreshToken(newUser.id))))
      when(patientRepository.createProfile(newUser.id, newUser.email))
        .thenReturn(Future.successful(Fixtures.patientProfile(userId = newUser.id)))
      when(userRepository.createEmailVerification(eqTo(newUser.id), any[String], eqTo(86400L)))
        .thenReturn(Future.successful(Fixtures.emailVerification(newUser.id)))
      when(notificationService.sendEmailVerification(eqTo(newUser.email), any[String]))
        .thenReturn(Future.successful(()))

      val result = service.signUp(newUser.email, "password123", UserRole.Patient).futureValue

      result mustBe a[Right[_, _]]
      verify(patientRepository).createProfile(newUser.id, newUser.email)
      verify(surgeonRepository, never).createProfile(any[UUID], any[String])
    }

    "create a surgeon profile when signing up as a surgeon" in {
      val newUser = Fixtures.user(role = UserRole.Surgeon)
      when(userRepository.emailExists(newUser.email)).thenReturn(Future.successful(false))
      when(userRepository.createUserWithRefreshToken(eqTo(newUser.email), any[String], eqTo(UserRole.Surgeon), any[String], any[Instant], any[Option[String]], any[Option[String]]))
        .thenReturn(Future.successful((newUser, Fixtures.refreshToken(newUser.id))))
      when(surgeonRepository.createProfile(newUser.id, newUser.email))
        .thenReturn(Future.successful(Fixtures.surgeonProfile(userId = newUser.id)))
      when(userRepository.createEmailVerification(eqTo(newUser.id), any[String], eqTo(86400L)))
        .thenReturn(Future.successful(Fixtures.emailVerification(newUser.id)))
      when(notificationService.sendEmailVerification(eqTo(newUser.email), any[String]))
        .thenReturn(Future.successful(()))

      val result = service.signUp(newUser.email, "password123", UserRole.Surgeon).futureValue

      result mustBe a[Right[_, _]]
      verify(surgeonRepository).createProfile(newUser.id, newUser.email)
      verify(patientRepository, never).createProfile(any[UUID], any[String])
    }

    "still succeed even if the verification email fails to send" in {
      val newUser = Fixtures.user(role = UserRole.Patient)
      when(userRepository.emailExists(newUser.email)).thenReturn(Future.successful(false))
      when(userRepository.createUserWithRefreshToken(eqTo(newUser.email), any[String], eqTo(UserRole.Patient), any[String], any[Instant], any[Option[String]], any[Option[String]]))
        .thenReturn(Future.successful((newUser, Fixtures.refreshToken(newUser.id))))
      when(patientRepository.createProfile(newUser.id, newUser.email))
        .thenReturn(Future.successful(Fixtures.patientProfile(userId = newUser.id)))
      when(userRepository.createEmailVerification(eqTo(newUser.id), any[String], eqTo(86400L)))
        .thenReturn(Future.successful(Fixtures.emailVerification(newUser.id)))
      when(notificationService.sendEmailVerification(eqTo(newUser.email), any[String]))
        .thenReturn(Future.failed(new RuntimeException("SMTP is down")))

      val result = service.signUp(newUser.email, "password123", UserRole.Patient).futureValue

      result mustBe a[Right[_, _]]
    }
  }

  "refresh" should {
    "reject an unknown or expired refresh token" in {
      when(userRepository.findRefreshToken(any[String])).thenReturn(Future.successful(None))

      val result = service.refresh("some-raw-token").futureValue

      result mustBe Left(AuthError.InvalidToken)
    }

    "reject a token whose user no longer exists" in {
      val userId = UUID.randomUUID()
      when(userRepository.findRefreshToken(any[String])).thenReturn(Future.successful(Some(Fixtures.refreshToken(userId))))
      when(userRepository.findById(userId)).thenReturn(Future.successful(None))

      val result = service.refresh("some-raw-token").futureValue

      result mustBe Left(AuthError.UserNotFound)
    }

    "reject a token belonging to an inactive user" in {
      val user = Fixtures.user(isActive = false)
      when(userRepository.findRefreshToken(any[String])).thenReturn(Future.successful(Some(Fixtures.refreshToken(user.id))))
      when(userRepository.findById(user.id)).thenReturn(Future.successful(Some(user)))

      val result = service.refresh("some-raw-token").futureValue

      result mustBe Left(AuthError.UserInactive)
    }

    "rotate the token and issue a new pair for a valid token" in {
      val user = Fixtures.user()
      val stored = Fixtures.refreshToken(user.id)
      when(userRepository.findRefreshToken(any[String])).thenReturn(Future.successful(Some(stored)))
      when(userRepository.findById(user.id)).thenReturn(Future.successful(Some(user)))
      when(userRepository.revokeRefreshToken(stored.tokenHash)).thenReturn(Future.successful(1))

      val result = service.refresh("some-raw-token").futureValue

      result mustBe a[Right[_, _]]
      verify(userRepository).revokeRefreshToken(stored.tokenHash)
    }
  }

  "signOut" should {
    "revoke the refresh token" in {
      when(userRepository.revokeRefreshToken(any[String])).thenReturn(Future.successful(1))

      service.signOut("some-raw-token").futureValue

      verify(userRepository).revokeRefreshToken(any[String])
    }
  }

  "signOutAll" should {
    "revoke every refresh token for the user" in {
      val userId = UUID.randomUUID()
      when(userRepository.revokeAllRefreshTokensForUser(userId)).thenReturn(Future.successful(3))

      service.signOutAll(userId).futureValue

      verify(userRepository).revokeAllRefreshTokensForUser(userId)
    }
  }

  "verifyEmail" should {
    "reject an unknown or expired token" in {
      when(userRepository.findEmailVerification("bad-token")).thenReturn(Future.successful(None))

      val result = service.verifyEmail("bad-token").futureValue

      result mustBe Left(AuthError.InvalidToken)
    }

    "mark the verification used and the user verified for a valid token" in {
      val userId = UUID.randomUUID()
      val ev = Fixtures.emailVerification(userId, token = "good-token")
      when(userRepository.findEmailVerification("good-token")).thenReturn(Future.successful(Some(ev)))
      when(userRepository.markEmailVerificationUsed(ev.id)).thenReturn(Future.successful(1))
      when(userRepository.markEmailVerified(userId)).thenReturn(Future.successful(1))

      val result = service.verifyEmail("good-token").futureValue

      result mustBe Right(())
      verify(userRepository).markEmailVerificationUsed(ev.id)
      verify(userRepository).markEmailVerified(userId)
    }
  }

  "initiatePasswordReset" should {
    "return None for an unknown email without revealing that it doesn't exist" in {
      when(userRepository.findByEmail("ghost@example.com")).thenReturn(Future.successful(None))

      val result = service.initiatePasswordReset("ghost@example.com").futureValue

      result mustBe None
      verify(userRepository, never).createPasswordReset(any[UUID], any[String], any[Long])
    }

    "create a reset record and return the raw token for a known email" in {
      val user = Fixtures.user()
      when(userRepository.findByEmail(user.email)).thenReturn(Future.successful(Some(user)))
      when(userRepository.createPasswordReset(eqTo(user.id), any[String], eqTo(3600L)))
        .thenReturn(Future.successful(Fixtures.passwordReset(user.id)))
      when(notificationService.sendPasswordReset(eqTo(user.email), any[String]))
        .thenReturn(Future.successful(()))

      val result = service.initiatePasswordReset(user.email).futureValue

      result mustBe defined
    }

    "still return the token even if the reset email fails to send" in {
      val user = Fixtures.user()
      when(userRepository.findByEmail(user.email)).thenReturn(Future.successful(Some(user)))
      when(userRepository.createPasswordReset(eqTo(user.id), any[String], eqTo(3600L)))
        .thenReturn(Future.successful(Fixtures.passwordReset(user.id)))
      when(notificationService.sendPasswordReset(eqTo(user.email), any[String]))
        .thenReturn(Future.failed(new RuntimeException("SMTP is down")))

      val result = service.initiatePasswordReset(user.email).futureValue

      result mustBe defined
    }
  }

  "completePasswordReset" should {
    "reject an unknown or expired token" in {
      when(userRepository.findPasswordReset(any[String])).thenReturn(Future.successful(None))

      val result = service.completePasswordReset("bad-token", "newPassword123").futureValue

      result mustBe Left(AuthError.InvalidToken)
    }

    "update the password and revoke all sessions for a valid token" in {
      val userId = UUID.randomUUID()
      val pr = Fixtures.passwordReset(userId, tokenHash = "reset-hash")
      when(userRepository.findPasswordReset(any[String])).thenReturn(Future.successful(Some(pr)))
      when(userRepository.markPasswordResetUsed(pr.id)).thenReturn(Future.successful(1))
      when(userRepository.updatePasswordHash(eqTo(userId), any[String])).thenReturn(Future.successful(1))
      when(userRepository.revokeAllRefreshTokensForUser(userId)).thenReturn(Future.successful(2))

      val result = service.completePasswordReset("good-token", "newPassword123").futureValue

      result mustBe Right(())
      verify(userRepository).markPasswordResetUsed(pr.id)
      verify(userRepository).updatePasswordHash(eqTo(userId), any[String])
      verify(userRepository).revokeAllRefreshTokensForUser(userId)
    }
  }
}
