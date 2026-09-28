// ─── test/co/auris/services/StorageServiceSpec.scala ──────────────────────────

package co.auris.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.Files.SingletonTemporaryFileCreator

import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext.Implicits.global

class StorageServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private def tempUploadFile(content: String = "fake-image-bytes"): play.api.libs.Files.TemporaryFile = {
    val tf = SingletonTemporaryFileCreator.create("upload", ".jpg")
    Files.write(tf.path, content.getBytes("UTF-8"))
    tf
  }

  private def config(extra: (String, Any)*): Configuration = {
    val defaults: Map[String, Any] = Map(
      "auris.storage.provider"      -> "local",
      "auris.storage.localPath"     -> Files.createTempDirectory("storage-spec").toString,
      "auris.storage.s3Region"      -> "eu-west-2",
      "auris.storage.maxFileSizeMb" -> 10,
      "auris.storage.allowedTypes"  -> Seq("image/jpeg", "image/png", "application/pdf")
    )
    Configuration.from(defaults ++ extra.toMap)
  }

  "store" should {
    "reject an unsupported content type regardless of provider" in {
      val service = new StorageService(config())
      val result = service.store(tempUploadFile(), "malware.exe", Some("application/x-msdownload"), "avatars").futureValue
      result mustBe Left(StorageError.UnsupportedType)
    }

    "reject a file over the configured size limit" in {
      val service = new StorageService(config("auris.storage.maxFileSizeMb" -> 0))
      val result = service.store(tempUploadFile(), "photo.jpg", Some("image/jpeg"), "avatars").futureValue
      result mustBe Left(StorageError.FileTooLarge)
    }
  }

  "store with provider = local" should {
    "write the file under localPath/<subdir> and return its /uploads URL" in {
      val localPath = Files.createTempDirectory("storage-spec-local")
      val service = new StorageService(config("auris.storage.localPath" -> localPath.toString))

      val result = service.store(tempUploadFile(), "photo.jpg", Some("image/jpeg"), "avatars").futureValue

      result mustBe a[Right[_, _]]
      val url = result.getOrElse(fail("expected Right"))
      url must startWith("/uploads/avatars/")
      url must endWith(".jpg")

      val storedFile: Path = localPath.resolve(url.stripPrefix("/uploads/"))
      Files.exists(storedFile) mustBe true
      Files.readString(storedFile) mustBe "fake-image-bytes"
    }
  }

  "store with provider = s3" should {
    "fail with S3NotConfigured when no bucket is set, without touching local disk" in {
      val localPath = Files.createTempDirectory("storage-spec-s3-unconfigured")
      val service = new StorageService(config(
        "auris.storage.provider"  -> "s3",
        "auris.storage.localPath" -> localPath.toString
        // s3Bucket deliberately omitted — matches this app's real config today
      ))

      val result = service.store(tempUploadFile(), "photo.jpg", Some("image/jpeg"), "avatars").futureValue

      result mustBe Left(StorageError.S3NotConfigured)
      Files.list(localPath).count() mustBe 0
    }

    "fail with S3NotConfigured when the bucket is set to an empty string" in {
      val service = new StorageService(config("auris.storage.provider" -> "s3", "auris.storage.s3Bucket" -> ""))

      val result = service.store(tempUploadFile(), "photo.jpg", Some("image/jpeg"), "avatars").futureValue

      result mustBe Left(StorageError.S3NotConfigured)
    }
  }
}
