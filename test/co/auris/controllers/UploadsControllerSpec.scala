// ─── test/co/auris/controllers/UploadsControllerSpec.scala ───────────────────
//
// Full-stack integration tests for GET /uploads/*file — most importantly the
// path-traversal guard: requested must stay inside auris.storage.localPath.

package co.auris.controllers

import co.auris.support.PlayIntegrationSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.test.FakeRequest

import java.nio.file.{Files, Path}

class UploadsControllerSpec extends AnyWordSpec with Matchers with PlayIntegrationSpec {

  private val uploadsRoot: Path = Files.createTempDirectory("auris-uploads-test")

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("auris.storage.localPath" -> uploadsRoot.toString)
      .build()

  private def writeFile(relativePath: String, content: String): Unit = {
    val target = uploadsRoot.resolve(relativePath)
    Files.createDirectories(target.getParent)
    Files.write(target, content.getBytes("UTF-8"))
    ()
  }

  // A real file just outside uploadsRoot (its immediate sibling) that a
  // single "../" traversal would land on — proves the guard actually stops
  // an escape that would otherwise succeed, not just that some unrelated
  // relative path 404s.
  private val secretFile: Path = {
    val file = uploadsRoot.getParent.resolve("auris-uploads-test-secret.txt")
    Files.write(file, "should never be served".getBytes("UTF-8"))
    file
  }

  "GET /uploads/*file" should {
    "serve a file that exists under the storage root" in {
      writeFile("avatars/photo.jpg", "fake-image-bytes")

      val res = route(app, FakeRequest(GET, "/uploads/avatars/photo.jpg")).get

      status(res) mustBe OK
    }

    "return 404 for a file that doesn't exist" in {
      val res = route(app, FakeRequest(GET, "/uploads/avatars/missing.jpg")).get

      status(res) mustBe NOT_FOUND
    }

    "return 404 for a directory rather than serving it" in {
      writeFile("avatars/nested/placeholder.txt", "x")

      val res = route(app, FakeRequest(GET, "/uploads/avatars/nested")).get

      status(res) mustBe NOT_FOUND
    }

    "refuse a path-traversal attempt that would otherwise resolve outside the storage root" in {
      // "../<secretFile name>" resolves, on disk, to a real file one
      // directory above uploadsRoot — if the guard were missing this would
      // succeed with 200, so a 404 here proves the guard actually fires.
      val res = route(app, FakeRequest(GET, s"/uploads/../${secretFile.getFileName}")).get

      status(res) mustBe NOT_FOUND
    }

    "refuse a deeper ../ escape even without a real target" in {
      val res = route(app, FakeRequest(GET, "/uploads/../../../../etc/passwd")).get

      status(res) mustBe NOT_FOUND
    }
  }
}
