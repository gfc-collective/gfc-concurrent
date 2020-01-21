package org.gfccollective.concurrent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers


class BatcherTest
  extends AnyFunSuite with Matchers with Eventually {
  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(3, Seconds)), interval = scaled(Span(3, Seconds)))

  test("check invalid input") {
    an [IllegalArgumentException] should be thrownBy {
      Batcher[Int]("test", -1, 1 seconds) { _ => }
    }

    an [IllegalArgumentException] should be thrownBy {
      Batcher[Int]("test", 1, -1 seconds) { _ => }
    }
  }


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


  test("batcher works after exceptions") {
    val records = (1 to 1000)

    val adder = new AtomicInteger()
    val counter = new AtomicInteger()

    val batcher = Batcher[Int](
      "test"
      , 1
      , 1 seconds
    ) { records =>
      if (counter.getAndIncrement() % 2 == 0) {
        throw new RuntimeException
      }
      records.foreach(i => adder.addAndGet(i))
    }

    records.foreach(i => batcher.add(i))

    batcher.flush()
    adder.intValue shouldBe records.filter(_ % 2 == 0).sum
    counter.intValue shouldBe records.size

    batcher.shutdown()
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
    val batchSize = 10
    val (batcher, adder, counter) = mkTestBatcher(batchSize)
    val records = (1 to 10000)

    val futures = records.map(i => Future{ batcher.add(i) } )
    Await.result(Future.sequence(futures), 5 seconds) // should flush after 2sec

    adder.intValue shouldBe records.sum
    counter.intValue shouldBe records.size / batchSize

    batcher.shutdown()
  }

  test("batcher seriously works concurrently") {
    val batchSize = 1
    val (batcher, adder, counter) = mkTestBatcher(batchSize)
    val records = (1 to 10000)
    val latch = new CountDownLatch(1)

    val futures = records.map(i => Future {
      latch.await()
      batcher.add(i)
    })

    Thread.sleep(1000L)
    latch.countDown()
    Await.result(Future.sequence(futures), 5 seconds) // should flush after 2sec

    adder.intValue shouldBe records.sum
    counter.intValue shouldBe records.size / batchSize

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
    val counter = new AtomicInteger()

    val batcher = Batcher[Int](
      "test"
    , maxOutstandingCount
    , 2 seconds
    ) { records =>
      counter.incrementAndGet()
      records.foreach(i => adder.addAndGet(i))
    }

    (batcher, adder, counter)
  }
}
