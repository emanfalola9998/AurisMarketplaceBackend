// ─── app/co/auris/controllers/UploadsController.scala ────────────────────────
//
// Serves files written by StorageService's local provider from disk.

package co.auris.controllers

import play.api.Configuration
import play.api.mvc._

import java.nio.file.{Files, Path, Paths}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class UploadsController @Inject() (
                                    cc:     ControllerComponents,
                                    config: Configuration
                                  )(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val root: Path =
    Paths.get(config.get[String]("auris.storage.localPath")).toAbsolutePath.normalize()

  def serve(file: String): Action[AnyContent] = Action {
    val requested = root.resolve(file).normalize()
    if (!requested.startsWith(root) || !Files.isRegularFile(requested)) {
      NotFound
    } else {
      Ok.sendPath(requested, inline = true)
    }
  }
}
