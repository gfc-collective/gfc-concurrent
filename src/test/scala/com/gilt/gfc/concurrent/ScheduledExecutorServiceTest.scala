package com.gilt.gfc.concurrent

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Callable, CountDownLatch, CyclicBarrier, Delayed, Executors, ScheduledFuture, TimeUnit, ScheduledExecutorService => JScheduledExecutorService}

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalactic.source.Position

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import com.gilt.gfc.time.Timer
import org.mockito.ArgumentCaptor
import org.scalatest.{Matchers => ScalaTestMatchers}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ScheduledExecutorServiceTest extends AnyFunSuite with Matchers with MockitoSugar with Eventually {
  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(3, Seconds)), interval = scaled(Span(3, Seconds)))

  val javaService = Executors.newScheduledThreadPool(20)

  val TimeStepMs = 500
  val FuzzFactor = 200

  test("asyncScheduleWithFixedDelay with mocks") {
    import ScalaFutures.Implicits.sameThreadExecutionContext

    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger

    def newFuture(): Future[Int] = {
      callCounter.incrementAndGet
      Future.successful(1)
    }

    service.asyncScheduleWithFixedDelay(1.second, 2.seconds)(newFuture)
    val callable: ArgumentCaptor[Callable[Unit]] = ArgumentCaptor.forClass(classOf[Callable[Unit]])

    verify(mockJavaService).schedule(callable.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    reset(mockJavaService)
    callCounter.get should be(0)

    callable.getValue.call

    verify(mockJavaService).schedule(callable.capture, ArgumentMatchers.eq(2000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    reset(mockJavaService)
    callCounter.get should be(1)

    callable.getValue.call

    verify(mockJavaService).schedule(callable.capture, ArgumentMatchers.eq(2000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    reset(mockJavaService)
    callCounter.get should be(2)
  }

  test("asyncScheduleAtFixedRate with mocks") {
    import ScalaFutures.Implicits.sameThreadExecutionContext

    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger

    def newFuture(): Future[Int] = {
      callCounter.incrementAndGet
      Future.successful(1)
    }

    service.asyncScheduleAtFixedRate(1.second, 2.seconds)(newFuture)
    val callable: ArgumentCaptor[Callable[Unit]] = ArgumentCaptor.forClass(classOf[Callable[Unit]])

    verify(mockJavaService).schedule(callable.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    reset(mockJavaService)
    callCounter.get should be(0)

    callable.getValue.call

    val rate = ArgumentCaptor.forClass(classOf[Long])
    verify(mockJavaService).schedule(callable.capture, rate.capture, ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    reset(mockJavaService)
    rate.getValue should be <= (2000L)
    rate.getValue should be > (1750L)
    callCounter.get should be(1)

    callable.getValue.call

    verify(mockJavaService).schedule(callable.capture, rate.capture, ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    reset(mockJavaService)
    rate.getValue should be <= (2000L)
    rate.getValue should be > (1750L)
    callCounter.get should be(2)
  }

  test("schedule(Callable, Long, TimeUnit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val callable = new Callable[Int] {
      override def call() = callCounter.incrementAndGet
    }
    val callableCaptor = ArgumentCaptor.forClass(classOf[Callable[Unit]])

    service.schedule(callable, 1000L, TimeUnit.MILLISECONDS)
    verify(mockJavaService).schedule(callableCaptor.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    callableCaptor.getValue.call
    callCounter.get should be(1)
  }

  test("schedule(Runnable, Long, TimeUnit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnable = new Runnable {
      override def run() = callCounter.incrementAndGet
    }
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.schedule(runnable, 1000L, TimeUnit.MILLISECONDS)
    verify(mockJavaService).schedule(runnableCaptor.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("scheduleWithFixedDelay(Runnable, Long, Long, TimeUnit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnable = new Runnable {
      override def run() = callCounter.incrementAndGet
    }
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.scheduleWithFixedDelay(runnable, 1000L, 2000L, TimeUnit.MILLISECONDS)
    verify(mockJavaService).scheduleWithFixedDelay(runnableCaptor.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(2000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("scheduleWithFixedDelay(Long, Long, TimeUnit)(=> Unit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.scheduleWithFixedDelay(1000L, 2000L, TimeUnit.MILLISECONDS)(callCounter.incrementAndGet)
    verify(mockJavaService).scheduleWithFixedDelay(runnableCaptor.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(2000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("scheduleWithFixedDelay(FiniteDuration, FiniteDuration)(=> Unit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.scheduleWithFixedDelay(1.second, 2.seconds)(callCounter.incrementAndGet)
    verify(mockJavaService).scheduleWithFixedDelay(runnableCaptor.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(2000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("scheduleAtFixedRate(Runnable, Long, Long, TimeUnit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnable = new Runnable {
      override def run() = callCounter.incrementAndGet
    }
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.scheduleAtFixedRate(runnable, 1000L, 2000L, TimeUnit.MILLISECONDS)
    verify(mockJavaService).scheduleAtFixedRate(runnableCaptor.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(2000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("scheduleAtFixedRate(Long, Long, TimeUnit)(=> Unit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.scheduleAtFixedRate(1000L, 2000L, TimeUnit.MILLISECONDS)(callCounter.incrementAndGet)
    verify(mockJavaService).scheduleAtFixedRate(runnableCaptor.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(2000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("scheduleAtFixedRate(FiniteDuration, FiniteDuration)(=> Unit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.scheduleAtFixedRate(1.second, 2.seconds)(callCounter.incrementAndGet)
    verify(mockJavaService).scheduleAtFixedRate(runnableCaptor.capture, ArgumentMatchers.eq(1000L), ArgumentMatchers.eq(2000L), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("execute(=> Unit)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.execute(callCounter.set(1))
    verify(mockJavaService).execute(runnableCaptor.capture)
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("submit(Callable)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val callable = new Callable[Int] {
      override def call() = callCounter.incrementAndGet
    }
    val callableCaptor: ArgumentCaptor[Callable[Int]] = ArgumentCaptor.forClass(classOf[Callable[Int]])

    service.submit(callable)
    verify(mockJavaService).submit(callableCaptor.capture)
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    callableCaptor.getValue.call
    callCounter.get should be(1)
  }

  test("submit(Runnable)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnable = new Runnable {
      override def run() = callCounter.incrementAndGet
    }
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])

    service.submit(runnable)
    verify(mockJavaService).submit(runnableCaptor.capture)
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("submit(Runnable, T)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val runnable = new Runnable {
      override def run() = callCounter.incrementAndGet
    }
    val runnableCaptor: ArgumentCaptor[Runnable] = ArgumentCaptor.forClass(classOf[Runnable])
    val t = "hello"

    service.submit(runnable, t)
    verify(mockJavaService).submit(runnableCaptor.capture, ArgumentMatchers.eq("hello"))
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    runnableCaptor.getValue.run
    callCounter.get should be(1)
  }

  test("submit(=> T)") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val callCounter = new AtomicInteger
    val callableCaptor: ArgumentCaptor[Callable[Int]] = ArgumentCaptor.forClass(classOf[Callable[Int]])

    service.submit(callCounter.incrementAndGet)
    verify(mockJavaService).submit(callableCaptor.capture)
    verifyNoMoreInteractions(mockJavaService)
    callCounter.get should be(0)
    callableCaptor.getValue.call
    callCounter.get should be(1)
  }

  test("JExecutorServiceWrapper pass-through functions") {
    val mockJavaService = mock[JScheduledExecutorService]
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    import scala.collection.JavaConverters._
    val tasks = Seq(new Callable[Int] {
      override def call() = 10
    }).asJavaCollection

    service.invokeAny(tasks)
    verify(mockJavaService).invokeAny(tasks)

    service.invokeAny(tasks, 1000L, TimeUnit.MILLISECONDS)
    verify(mockJavaService).invokeAny(tasks, 1000L, TimeUnit.MILLISECONDS)

    service.invokeAll(tasks)
    verify(mockJavaService).invokeAll(tasks)

    service.invokeAll(tasks, 1000L, TimeUnit.MILLISECONDS)
    verify(mockJavaService).invokeAll(tasks, 1000L, TimeUnit.MILLISECONDS)

    service.isTerminated
    verify(mockJavaService).isTerminated

    service.isShutdown
    verify(mockJavaService).isShutdown

    service.shutdown
    verify(mockJavaService).shutdown

    service.shutdownNow
    verify(mockJavaService).shutdownNow

    service.awaitTermination(1000L, TimeUnit.MILLISECONDS)
    verify(mockJavaService).awaitTermination(1000L, TimeUnit.MILLISECONDS)
  }

  test("ScheduledFutureWrapper wraps ScheduledFuture") {
    import ScalaFutures.Implicits.sameThreadExecutionContext

    val mockFuture: ScheduledFuture[Int] = mock[ScheduledFuture[Int]]
    val mockJavaService = mock[JScheduledExecutorService]
    doReturn(mockFuture, null).when(mockJavaService).schedule(ArgumentMatchers.any[Callable[_]](), ArgumentMatchers.anyLong(), ArgumentMatchers.any[TimeUnit]())
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    val wrapper = service.asyncScheduleWithFixedDelay(1.second, 1.seconds)(Future.successful(1))

    wrapper.isCancelled
    wrapper.isDone
    verifyNoMoreInteractions(mockFuture)

    wrapper.getDelay(TimeUnit.MILLISECONDS)
    verify(mockFuture).getDelay(TimeUnit.MILLISECONDS)

    wrapper.get
    verify(mockFuture).get

    wrapper.get(1000L, TimeUnit.MILLISECONDS)
    verify(mockFuture).get

    val delayed = mock[Delayed]
    wrapper.compareTo(delayed)
    verify(mockFuture).compareTo(delayed)

    reset(mockFuture)

    wrapper.cancel(true)
    verify(mockFuture).cancel(true)

    wrapper.isDone
    verify(mockFuture).isDone

    wrapper.isCancelled
    verifyNoMoreInteractions(mockFuture)
  }

  test("blows on schedule") {
    val toThrow = new RuntimeException("boom")
    val mockJavaService = mock[JScheduledExecutorService]
    when(mockJavaService.schedule(ArgumentMatchers.any[Callable[_]], ArgumentMatchers.anyLong, ArgumentMatchers.any)).thenThrow(toThrow)
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = mockJavaService
    }

    def newFuture(): Future[Int] = fail("should not have called newFuture")

    val caught = the [RuntimeException] thrownBy {
      service.asyncScheduleWithFixedDelay(1.second, 2.seconds)(newFuture)
    }

    caught should be(toThrow)
  }

  test("exception thrown in futureTask") {
    val toThrow = new RuntimeException("boom")
    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = javaService
    }

    val latch = new CountDownLatch(3)
    def newFuture(): Future[Int] = {
      latch.countDown
      throw toThrow
    }

    service.asyncScheduleWithFixedDelay(0.millis, TimeStepMs.millis)(newFuture)

    latch.await(10 * TimeStepMs, TimeUnit.MILLISECONDS) should be(true)
  }

  test("asyncScheduleWithFixedDelay sticks to delay") {
    implicit val executor = ExecutionContext.fromExecutorService(javaService)

    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = javaService
    }

    val barrier = new CyclicBarrier(2)
    def newFuture(): Future[Int] = Future {
      barrier.await()
      Thread.sleep(TimeStepMs)
      1
    }

    service.asyncScheduleWithFixedDelay(TimeStepMs.millis, TimeStepMs.millis)(newFuture)

    checkFuzzyTiming(TimeStepMs)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
    checkFuzzyTiming(2 * TimeStepMs)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
  }

  test("asyncScheduleAtFixedRate sticks to rate") {
    implicit val executor = ExecutionContext.fromExecutorService(javaService)

    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = javaService
    }

    val barrier = new CyclicBarrier(2)
    def newFuture(): Future[Int] = Future {
      barrier.await()
      Thread.sleep(TimeStepMs / 2)
      1
    }

    service.asyncScheduleAtFixedRate(TimeStepMs.millis, TimeStepMs.millis)(newFuture)

    checkFuzzyTiming(TimeStepMs)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
    checkFuzzyTiming(TimeStepMs)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
  }

  test("asyncScheduleAtFixedRate reschedules immediately if task overruns rate") {
    implicit val executor = ExecutionContext.fromExecutorService(javaService)

    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = javaService
    }

    val barrier = new CyclicBarrier(2)
    def newFuture(): Future[Int] = Future {
      barrier.await()
      Thread.sleep(2 * TimeStepMs)
      1
    }

    service.asyncScheduleAtFixedRate(0.millis, TimeStepMs.millis)(newFuture)

    checkFuzzyTiming(0)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
    checkFuzzyTiming(2 * TimeStepMs)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
    checkFuzzyTiming(2 * TimeStepMs)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
  }

  test("cancel cancels scheduled task") {
    implicit val executor = ExecutionContext.fromExecutorService(javaService)

    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = javaService
    }

    val barrier = new CyclicBarrier(2)
    def newFuture(): Future[Int] = {
      barrier.await()
      Future.successful(1)
    }

    val future = service.asyncScheduleAtFixedRate(0.millis, TimeStepMs.millis)(newFuture)

    checkFuzzyTiming(0)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
    checkFuzzyTiming(TimeStepMs)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
    future.cancel(false)

    Thread.sleep(2 * TimeStepMs)
    barrier.getNumberWaiting should be(0)
  }

  test("single-thread scheduled executor #submit Scala function sanity check") {
    import com.gilt.gfc.concurrent.JavaConverters._
    val n = new AtomicInteger(0)
    val javaExecutor = Executors.newSingleThreadScheduledExecutor
    val scalaExecutor = javaExecutor.asScala
    scalaExecutor.submit {
      n.incrementAndGet
    }
    eventually({ n.intValue should be > 0 })(patienceConfig, Position.here)
  }

  test("single-thread scheduled executor #execute(javaRunnable) sanity check") {
    import com.gilt.gfc.concurrent.JavaConverters._
    val n = new AtomicInteger(0)
    val javaExecutor = Executors.newSingleThreadScheduledExecutor
    val scalaExecutor = javaExecutor.asScala
    val runnable = new Runnable() {
      override def run(): Unit = n.incrementAndGet
    }
    scalaExecutor.execute(runnable)
    eventually({ n.intValue should be > 0 })(patienceConfig, Position.here)
  }

  test("cancel does not reschedule") {
    implicit val executor = ExecutionContext.fromExecutorService(javaService)

    val service = new JScheduledExecutorServiceWrapper {
      override val executorService: JScheduledExecutorService = javaService
    }

    val barrier = new CyclicBarrier(2)
    def newFuture(): Future[Int] = Future {
      barrier.await()
      Thread.sleep(TimeStepMs)
      1
    }

    val future = service.asyncScheduleAtFixedRate(0.millis, TimeStepMs.millis)(newFuture)

    checkFuzzyTiming(0)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
    checkFuzzyTiming(TimeStepMs)(barrier.await(5 * TimeStepMs, TimeUnit.MILLISECONDS))
    future.cancel(false)

    Thread.sleep(2 * TimeStepMs)
    barrier.getNumberWaiting should be(0)
  }

  def checkFuzzyTiming[T](exactMs: Long, fuzziness: Long = FuzzFactor)(f: => T): T = {
    val minMs = Seq(0, exactMs - fuzziness).max
    val maxMs = exactMs + fuzziness
    Timer.time { nanos =>
      (nanos / 1000000) should ((be >= (minMs)) and (be <=(maxMs)))
    }(f)
  }
}
