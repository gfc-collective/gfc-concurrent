package com.gilt.gfc.concurrent

import java.util.concurrent.{ TimeoutException, TimeUnit }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TimeoutsSpec extends AnyWordSpec with Matchers {
  import TimeoutsSpec._

  "Timeouts" when {
    "generating timing out futures" should {
      "create a Future that times out after the given finite duration" in {
        val now = System.currentTimeMillis
        val after = FiniteDuration(1, "second")
        val timingOut = Timeouts.timeout(after)
        an [TimeoutException] should be thrownBy { Await.result(timingOut, Duration(10, "seconds")) }
        val elapsed = (System.currentTimeMillis - now)
        elapsed should be (after.toMillis +- 500L)
      }

      "create timing out Futures that will fail predictably even under load" in {
        import scala.util.Random._

        val MaxTimeout = Duration(10, "seconds").toMillis.toInt
        val MaxDelta = Duration(50, "milliseconds").toMillis
        val Load = 10000

        val timingOuts: List[(Future[Nothing], Duration)] = (1 to Load).map { i =>
          val after = Duration(nextInt(MaxTimeout), "milliseconds")
          val timingOut = Timeouts.timeout(after)
          (timingOut, after)
        }.toList

        val timedOuts: List[(Future[Nothing], Duration, Duration, Duration)] = timingOuts.map { case (timingOut, after) =>
          val thrown = the [TimeoutException] thrownBy { Await.result(timingOut, Duration.Inf) }
          // println(thrown)
          val real = Duration(extractReal(thrown.getCause.getMessage), TimeUnit.MILLISECONDS)
          val delta = Duration(real.toMillis - after.toMillis, TimeUnit.MILLISECONDS)
          (timingOut, after, real, delta)
        }

        timedOuts.filter { case (timedOut, after, real, delta) => delta.toMillis > MaxDelta }.size === 0
      }

      "include the origin of the future" in {
        val here = new Exception()
        val timingOut = Timeouts.timeout(1.millis)
        val thrown = the [TimeoutException] thrownBy { Await.result(timingOut, Duration(10, "seconds")) }
        thrown.getStackTrace.size shouldBe > (50)
        val thrownFrames = thrown.getStackTrace.map(f => f: AnyRef).drop(7)
        val expectedFrames = here.getStackTrace.map(f => f: AnyRef)
        // Scala 2.12 stack frames differ slightly to stack frames in 2.10/2.11
        if (!java.util.Arrays.deepEquals(thrownFrames, expectedFrames.drop(2))) {
          thrownFrames shouldBe expectedFrames.drop(1)
        }
        thrown.getCause should not be null
        thrown.getCause.getStackTrace.size shouldBe <= (10)

      }
    }
  }
}

object TimeoutsSpec {
  private val pattern = """real: (\d+) ms.""".r

  def extractReal(s: String): Int = try {
    pattern.findFirstMatchIn(s).map { _.toString.split("real: ") }.get(1).split(" ms.").head.toInt
  } catch {
    case ex: Exception => throw new RuntimeException(s"Unable to parse real time from '${s}'", ex)
  }
}
