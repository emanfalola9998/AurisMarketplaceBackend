// ─── test/co/auris/services/RateLimiterSpec.scala ─────────────────────────────

package co.auris.services

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt

class RateLimiterSpec extends AnyWordSpec with Matchers {

  "RateLimiter" should {
    "allow attempts up to the limit" in {
      val limiter = new RateLimiter
      (1 to 5).foreach { _ =>
        limiter.tryAcquire("key", maxAttempts = 5, window = 1.minute) mustBe true
      }
    }

    "reject attempts once the limit is exceeded" in {
      val limiter = new RateLimiter
      (1 to 5).foreach { _ => limiter.tryAcquire("key", maxAttempts = 5, window = 1.minute) }

      limiter.tryAcquire("key", maxAttempts = 5, window = 1.minute) mustBe false
    }

    "track different keys independently" in {
      val limiter = new RateLimiter
      (1 to 5).foreach { _ => limiter.tryAcquire("key-a", maxAttempts = 5, window = 1.minute) }

      limiter.tryAcquire("key-b", maxAttempts = 5, window = 1.minute) mustBe true
    }

    "reset the count once the window has elapsed" in {
      val limiter = new RateLimiter
      (1 to 5).foreach { _ => limiter.tryAcquire("key", maxAttempts = 5, window = 1.millisecond) }
      Thread.sleep(10)

      limiter.tryAcquire("key", maxAttempts = 5, window = 1.millisecond) mustBe true
    }
  }
}
