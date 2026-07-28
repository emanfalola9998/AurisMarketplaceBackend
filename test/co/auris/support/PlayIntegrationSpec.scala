// ─── test/co/auris/support/PlayIntegrationSpec.scala ──────────────────────────
//
// Base spec for controller integration tests: boots a real Play application
// (Guice DI, real routes, real JwtAuthAction) against the auris_test
// database — a genuinely separate Postgres database from auris_dev, not a
// schema (see application.test.conf for why). Truncates the auth-relevant
// tables before every test so specs can use fixed, readable emails without
// colliding across runs.
//
// SAFETY: two earlier versions of this tried to isolate via a shared
// database + schema/search_path trick (JDBC `currentSchema` URL param, then
// `connectionInitSql`) and both silently failed to scope connections away
// from public — the test suite's TRUNCATE ran against real auris_dev data
// both times. current_database() has none of that ambiguity: it reflects
// the actual TCP target of the physical connection, fixed at connect time
// by the JDBC URL, not something a session-level SET can quietly miss.
// requireTestDatabase() checks it before every single truncate and refuses
// to run (hard failure, not a warning) if it isn't exactly "auris_test".

package co.auris.support

import org.scalatest.BeforeAndAfterEach
import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.db.slick.DatabaseConfigProvider
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

trait PlayIntegrationSpec extends GuiceOneAppPerSuite with BeforeAndAfterEach { self: TestSuite =>

  protected lazy val dbConfig: DatabaseConfig[JdbcProfile] = app.injector.instanceOf[DatabaseConfigProvider].get[JdbcProfile]
  protected lazy val db: JdbcProfile#Backend#Database = dbConfig.db

  private val expectedDatabase = "auris_test"
  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private def requireTestDatabase(): Unit = {
    val actual = Await.result(db.run(sql"SELECT current_database()".as[String]).map(_.head), 10.seconds)
    require(
      actual == expectedDatabase,
      s"""Refusing to truncate: current_database() is '$actual', expected '$expectedDatabase'.
         |This check exists because two earlier schema-based isolation attempts here
         |both silently ran against auris_dev's real data instead.
         |Check slick.dbs.default.db.url in application.test.conf.""".stripMargin
    )
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    requireTestDatabase()
    val truncate = sqlu"""
      TRUNCATE TABLE
        refresh_tokens, email_verifications, password_resets,
        patient_profiles, surgeon_profiles, users
      RESTART IDENTITY CASCADE
    """
    Await.result(db.run(truncate), 10.seconds)
    ()
  }
}
