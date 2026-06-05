// ─── app/co/auris/filters/RequestLoggingFilter.scala ─────────────────────────
//
// Logs every request with method, path, status, and duration.
// Attaches a correlation ID to each request for Kibana/Grafana tracing.

package co.auris.filters

import akka.stream.Materializer
import play.api.Logging
import play.api.mvc._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RequestLoggingFilter @Inject() (implicit
                                      val mat: Materializer,
                                      ec:      ExecutionContext
                                     ) extends Filter
  with Logging {

  override def apply(
                      nextFilter: RequestHeader => Future[Result]
                    )(rh: RequestHeader): Future[Result] = {

    // Propagate or generate a correlation ID
    val correlationId = rh.headers
      .get("X-Correlation-ID")
      .getOrElse(UUID.randomUUID().toString)

    val startTime = System.currentTimeMillis()
    val taggedRh  = rh.withHeaders(rh.headers.add("X-Correlation-ID" -> correlationId))

    nextFilter(taggedRh).transform {
      case Success(result) =>
        val duration = System.currentTimeMillis() - startTime
        logger.info(
          s"[${correlationId.take(8)}] ${rh.method} ${rh.path} → ${result.header.status} (${duration}ms)"
        )
        Success(result.withHeaders("X-Correlation-ID" -> correlationId))

      case Failure(ex) =>
        val duration = System.currentTimeMillis() - startTime
        logger.error(
          s"[${correlationId.take(8)}] ${rh.method} ${rh.path} → ERROR (${duration}ms): ${ex.getMessage}"
        )
        Failure(ex)
    }
  }
}
