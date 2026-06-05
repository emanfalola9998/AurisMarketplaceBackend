// ─── app/co/auris/controllers/HealthController.scala ─────────────────────────

package co.auris.controllers

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.Json
import play.api.mvc._
import slick.jdbc.JdbcProfile
import co.auris.db.AurisPostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class HealthController @Inject() (
                                   cc:                          ControllerComponents,
                                   protected val dbConfigProvider: DatabaseConfigProvider
                                 )(implicit ec: ExecutionContext)
  extends AbstractController(cc)
    with HasDatabaseConfigProvider[JdbcProfile] {

  /** GET /api/health — returns 200 if DB is reachable, 503 otherwise. */
  def check: Action[AnyContent] = Action.async {
    db.run(sql"SELECT 1".as[Int]).map { _ =>
      Ok(Json.obj("status" -> "ok", "db" -> "ok"))
    }.recover { case _ =>
      ServiceUnavailable(Json.obj("status" -> "degraded", "db" -> "unreachable"))
    }
  }
}