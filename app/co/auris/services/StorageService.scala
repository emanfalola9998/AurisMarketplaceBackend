// ─── app/co/auris/services/StorageService.scala ───────────────────────────────
//
// File storage for surgeon avatars and portfolio photos. Reads config from
// auris.storage.* (see application.conf) and dispatches on
// auris.storage.provider — "local" writes to disk (served back by
// UploadsController), "s3" uploads to the configured bucket and returns its
// public URL.

package co.auris.services

import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait StorageError
object StorageError {
  case object FileTooLarge     extends StorageError
  case object UnsupportedType  extends StorageError
  // auris.storage.provider = "s3" but no bucket is configured — a server
  // misconfiguration, not something the uploading user can fix.
  case object S3NotConfigured  extends StorageError
}

@Singleton
class StorageService @Inject() (config: Configuration)(implicit ec: ExecutionContext) {

  private val provider         = config.get[String]("auris.storage.provider")
  private val localPath: Path  = Paths.get(config.get[String]("auris.storage.localPath"))
  private val maxFileSizeBytes = config.get[Int]("auris.storage.maxFileSizeMb") * 1024L * 1024L
  private val allowedTypes: Set[String] = config.get[Seq[String]]("auris.storage.allowedTypes").toSet

  private val s3Region                 = config.get[String]("auris.storage.s3Region")
  private val s3Bucket: Option[String] = config.getOptional[String]("auris.storage.s3Bucket").filter(_.nonEmpty)

  // Built lazily, and only ever referenced from storeToS3 below — local-only
  // deployments (and every test/dev environment today) never touch this, so
  // there's no AWS credential resolution attempted unless provider = "s3".
  private lazy val s3Client: S3Client =
    S3Client.builder()
      .region(Region.of(s3Region))
      .credentialsProvider(DefaultCredentialsProvider.create())
      .build()

  /** Stores the file and returns its public URL. Dispatches on
   *  auris.storage.provider — see the class comment.
   */
  def store(
             file:        TemporaryFile,
             filename:    String,
             contentType: Option[String],
             subdir:      String
           ): Future[Either[StorageError, String]] = {
    if (!contentType.exists(allowedTypes.contains)) {
      Future.successful(Left(StorageError.UnsupportedType))
    } else if (Files.size(file.path) > maxFileSizeBytes) {
      Future.successful(Left(StorageError.FileTooLarge))
    } else {
      val dotIndex   = filename.lastIndexOf('.')
      val ext        = if (dotIndex >= 0) filename.substring(dotIndex) else ""
      val storedName = s"${UUID.randomUUID()}$ext"

      provider match {
        case "s3" => storeToS3(file.path, subdir, storedName, contentType)
        case _    => storeLocally(file.path, subdir, storedName)
      }
    }
  }

  private def storeLocally(path: Path, subdir: String, storedName: String): Future[Either[StorageError, String]] = Future {
    val targetDir = localPath.resolve(subdir)
    Files.createDirectories(targetDir)
    Files.copy(path, targetDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING)
    Right(s"/uploads/$subdir/$storedName")
  }

  private def storeToS3(
                         path:        Path,
                         subdir:      String,
                         storedName:  String,
                         contentType: Option[String]
                       ): Future[Either[StorageError, String]] =
    s3Bucket match {
      case None => Future.successful(Left(StorageError.S3NotConfigured))
      case Some(bucket) =>
        Future {
          val key = s"$subdir/$storedName"
          val requestBuilder = PutObjectRequest.builder().bucket(bucket).key(key)
          contentType.foreach(requestBuilder.contentType)
          s3Client.putObject(requestBuilder.build(), RequestBody.fromFile(path))
          Right(s"https://$bucket.s3.$s3Region.amazonaws.com/$key")
        }
    }
}
