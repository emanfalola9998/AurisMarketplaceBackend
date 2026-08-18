// ─── app/co/auris/services/RateLimiter.scala ──────────────────────────────────
//
// In-memory, per-key fixed-window rate limiter. Single-instance only — if
// Auris is ever scaled to multiple app instances, this needs to move to a
// shared store (Redis INCR + EXPIRE) since each instance would otherwise
// track its own independent window.

package co.auris.services

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton
import scala.concurrent.duration.FiniteDuration

@Singleton
class RateLimiter {

  private class Bucket {
    var windowStart: Long = System.currentTimeMillis()
    var count:       Int  = 0
  }

  private val buckets = new ConcurrentHashMap[String, Bucket]()

  /** Records one attempt under `key` and returns whether it's within the limit. */
  def tryAcquire(key: String, maxAttempts: Int, window: FiniteDuration): Boolean = {
    val bucket = buckets.computeIfAbsent(key, _ => new Bucket)
    bucket.synchronized {
      val now = System.currentTimeMillis()
      if (now - bucket.windowStart > window.toMillis) {
        bucket.windowStart = now
        bucket.count = 0
      }
      bucket.count += 1
      bucket.count <= maxAttempts
    }
  }
}
