package com.gilt.gfc.concurrent

import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}


class BatcherTest
  extends FunSuite with Matchers with Eventually {
  implicit val patience = PatienceConfig(scaled(Span(500, Millis)))

  test("batcher works single-threadedly") {
    for ( maxOutstanding <- (1 to 10) ;
          numRecords <- (1 to 100) ) {

      val records = (1 to numRecords)
      val (batcher, adder, counter) = mkTestBatcher(maxOutstanding)
      records.foreach(i => batcher.add(i))

      batcher.flush()
      adder.intValue shouldBe records.sum
      counter.intValue shouldBe (numRecords + maxOutstanding - 1) / maxOutstanding

      batcher.shutdown()
    }
  }


  test("batcher flushes after a period of time") {
    val records = (1 to 10)
    val (batcher, adder, counter) = mkTestBatcher(100)
    records.foreach(i => batcher.add(i))

    Thread.sleep(2001L) // should flush after 2sec

    eventually {
      adder.intValue shouldBe records.sum
    }

    counter.intValue shouldBe 1

    batcher.shutdown()
  }


  test("batcher flushes after max outstanding count") {
    val records = (1 to 9)
    val (batcher, adder, counter) = mkTestBatcher(5)
    records.foreach(i => batcher.add(i))
    adder.intValue shouldBe (1 to 5).sum // should see 5 out of 9
    counter.intValue shouldBe 1

    batcher.shutdown()
  }


  test("batcher works concurrently") {
    val (batcher, adder, _) = mkTestBatcher(10)
    val records = (1 to 10000)

    val futures = records.map(i => Future{ batcher.add(i) } )
    Await.result(Future.sequence(futures), 5 seconds) // should flush after 2sec

    adder.intValue shouldBe records.sum

    batcher.shutdown()
  }


  test("batcher adjusts next run to maxOutstandingDuration after reaching maxOutstandingCount") {
    val (batcher, adder, counter) = mkTestBatcher(2)
    Thread.sleep(1000L)

    // first add after 1sec, should not flush immediately
    batcher.add(1)
    // second add should flush (still after ~1sec)
    batcher.add(1)

    // should not flush again after 2sec schedule
    Thread.sleep(1100L)
    adder.intValue shouldBe 2
    counter.intValue shouldBe 1

    // third add should not flush immediately
    batcher.add(1)

    // should flush on schedule after ~3sec
    Thread.sleep(1000L)
    eventually {
      adder.intValue shouldBe 3
    }
    counter.intValue shouldBe 2

    batcher.shutdown()
  }


  private[this]
  def mkTestBatcher( maxOutstandingCount: Int
                   ): (Batcher[Int], AtomicInteger, AtomicInteger) = {
    val adder = new AtomicInteger()
    val callCounter = new AtomicInteger()

    val batcher = Batcher[Int](
      "test"
    , maxOutstandingCount
    , 2 seconds
    ) { records =>
      callCounter.incrementAndGet()
      records.foreach(i => adder.addAndGet(i))
    }

    (batcher, adder, callCounter)
  }
}
