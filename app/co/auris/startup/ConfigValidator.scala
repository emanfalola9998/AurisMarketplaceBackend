// ─── app/co/auris/startup/ConfigValidator.scala ────────────────────────────────
//
// Runs once at boot (see StartupModule — bound as an eager singleton so
// construction happens on application start, not on first use).
//
// Every sensitive setting in application.conf already has a
// ${?ENV_VAR} override, but nothing enforced that the override was actually
// supplied — deploying with the committed placeholder secrets still intact
// would work fine, just insecurely (a known/leaked JWT signing secret or
// Play application secret in production is an authentication-bypass and
// session-forgery risk, not a cosmetic issue). Only Mode.Prod is checked —
// `sbt run`/`sbt test` use Mode.Dev/Mode.Test and are never blocked, so this
// can't get in a developer's way locally.

package co.auris.startup

import play.api.{Configuration, Environment, Logging, Mode}

import javax.inject.{Inject, Singleton}

@Singleton
class ConfigValidator @Inject() (
                                  config:      Configuration,
                                  environment: Environment
                                ) extends Logging {

  private val PlaceholderSecretKey = "REPLACE_ME_LONG_RANDOM_STRING_MIN_32_CHARS"
  private val PlaceholderJwtSecret = "REPLACE_ME_JWT_SECRET_MIN_64_CHARS_RANDOM"

  if (environment.mode == Mode.Prod) {
    val secretKey = config.get[String]("play.http.secret.key")
    val jwtSecret = config.get[String]("auris.jwt.secret")

    val failures = List(
      Option.when(secretKey == PlaceholderSecretKey)(
        "play.http.secret.key is still the committed placeholder — set the PLAY_SECRET_KEY environment variable."),
      Option.when(jwtSecret == PlaceholderJwtSecret)(
        "auris.jwt.secret is still the committed placeholder — set the JWT_SECRET environment variable.")
    ).flatten

    if (failures.nonEmpty) {
      throw new IllegalStateException(
        ("Refusing to start in production with insecure defaults:" :: failures.map(f => s"  - $f")).mkString("\n")
      )
    }

    if (config.get[Boolean]("play.evolutions.db.default.autoApply")) {
      logger.warn(
        "play.evolutions.db.default.autoApply is true in production — schema evolutions will run " +
          "automatically on every boot. Set EVOLUTIONS_AUTO_APPLY=false to require reviewing and applying them manually."
      )
    }
    if (config.get[String]("stripe.secretKey").contains("REPLACE_ME")) {
      logger.warn("stripe.secretKey is still the committed placeholder — Stripe-backed features will fail until STRIPE_SECRET_KEY is set.")
    }
    if (config.get[Boolean]("play.mailer.mock")) {
      logger.warn("play.mailer.mock is true in production — transactional emails are being logged, not sent. Set MAILER_MOCK=false once real SMTP credentials are configured.")
    }
  }
}
