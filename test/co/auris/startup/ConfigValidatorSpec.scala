// ─── test/co/auris/startup/ConfigValidatorSpec.scala ──────────────────────────

package co.auris.startup

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.{Configuration, Environment, Mode}

class ConfigValidatorSpec extends AnyWordSpec with Matchers {

  private val PlaceholderSecretKey = "REPLACE_ME_LONG_RANDOM_STRING_MIN_32_CHARS"
  private val PlaceholderJwtSecret = "REPLACE_ME_JWT_SECRET_MIN_64_CHARS_RANDOM"

  private def config(
                      secretKey:     String  = "a-real-random-secret-that-is-long-enough-1234567890",
                      jwtSecret:     String  = "another-real-random-secret-that-is-plenty-long-enough-1234567890",
                      autoApply:     Boolean = false,
                      stripeKey:     String  = "sk_live_a_real_key",
                      mailerMock:    Boolean = false,
                      storageProvider: String = "local"
                    ): Configuration = Configuration(
    "play.http.secret.key"                  -> secretKey,
    "auris.jwt.secret"                      -> jwtSecret,
    "play.evolutions.db.default.autoApply"  -> autoApply,
    "stripe.secretKey"                      -> stripeKey,
    "play.mailer.mock"                      -> mailerMock,
    "auris.storage.provider"                -> storageProvider
  )

  "ConfigValidator" should {
    "refuse to start in production with a placeholder Play application secret" in {
      an[IllegalStateException] must be thrownBy
        new ConfigValidator(config(secretKey = PlaceholderSecretKey), Environment.simple(mode = Mode.Prod))
    }

    "refuse to start in production with a placeholder JWT secret" in {
      an[IllegalStateException] must be thrownBy
        new ConfigValidator(config(jwtSecret = PlaceholderJwtSecret), Environment.simple(mode = Mode.Prod))
    }

    "start fine in production once real secrets are configured" in {
      noException must be thrownBy new ConfigValidator(config(), Environment.simple(mode = Mode.Prod))
    }

    "never block startup in dev mode, even with placeholder secrets" in {
      noException must be thrownBy new ConfigValidator(
        config(secretKey = PlaceholderSecretKey, jwtSecret = PlaceholderJwtSecret),
        Environment.simple(mode = Mode.Dev)
      )
    }

    "never block startup in test mode, even with placeholder secrets" in {
      noException must be thrownBy new ConfigValidator(
        config(secretKey = PlaceholderSecretKey, jwtSecret = PlaceholderJwtSecret),
        Environment.simple(mode = Mode.Test)
      )
    }

    "only warn, not block, in production when the s3 provider has no bucket configured" in {
      noException must be thrownBy
        new ConfigValidator(config(storageProvider = "s3"), Environment.simple(mode = Mode.Prod))
    }

    "start fine in production with the s3 provider once a bucket is configured" in {
      val withBucket = Configuration("auris.storage.s3Bucket" -> "auris-uploads-prod").withFallback(config(storageProvider = "s3"))
      noException must be thrownBy new ConfigValidator(withBucket, Environment.simple(mode = Mode.Prod))
    }
  }
}
