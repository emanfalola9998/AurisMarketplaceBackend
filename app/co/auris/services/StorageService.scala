// ─── app/co/auris/services/StorageService.scala ───────────────────────────────
//
// Local-disk file storage for surgeon avatars and portfolio photos.
// Reads config from auris.storage.* (see application.conf). Only the
// "local" provider is implemented; s3Bucket/s3Region are reserved for
// a future S3-backed implementation behind the same interface.

package co.auris.services

import play.api.Configuration
import play.api.libs.Files.TemporaryFile

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait StorageError
object StorageError {
  case object FileTooLarge    extends StorageError
  case object UnsupportedType extends StorageError
}

@Singleton
class StorageService @Inject() (config: Configuration)(implicit ec: ExecutionContext) {

  private val localPath: Path   = Paths.get(config.get[String]("auris.storage.localPath"))
  private val maxFileSizeBytes  = config.get[Int]("auris.storage.maxFileSizeMb") * 1024L * 1024L
  private val allowedTypes: Set[String] = config.get[Seq[String]]("auris.storage.allowedTypes").toSet

  /** Stores the file under localPath/subdir/<uuid>.<ext> and returns its public URL path. */
  def store(
             file:        TemporaryFile,
             filename:    String,
             contentType: Option[String],
             subdir:      String
           ): Future[Either[StorageError, String]] = Future {
    if (!contentType.exists(allowedTypes.contains)) {
      Left(StorageError.UnsupportedType)
    } else if (Files.size(file.path) > maxFileSizeBytes) {
      Left(StorageError.FileTooLarge)
    } else {
      val dotIndex = filename.lastIndexOf('.')
      val ext      = if (dotIndex >= 0) filename.substring(dotIndex) else ""
      val storedName = s"${UUID.randomUUID()}$ext"
      val targetDir   = localPath.resolve(subdir)
      Files.createDirectories(targetDir)
      Files.copy(file.path, targetDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING)
      Right(s"/uploads/$subdir/$storedName")
    }
  }
}
