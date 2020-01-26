# gfc-concurrent [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.gfccollective/gfc-concurrent_2.12/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/org.gfccollective/gfc-concurrent_2.12) [![Build Status](https://github.com/gfc-collective/gfc-concurrent/workflows/Scala%20CI/badge.svg)](https://github.com/gfc-collective/gfc-concurrent/actions) [![Coverage Status](https://coveralls.io/repos/gfc-collective/gfc-concurrent/badge.svg?branch=master&service=github)](https://coveralls.io/github/gfc-collective/gfc-concurrent?branch=master)

A library that contains scala concurrency helper code. 
A fork and new home of the former Gilt Foundation Classes (`com.gilt.gfc`), now called the [GFC Collective](https://github.com/gfc-collective), maintained by some of the original authors.

## Getting gfc-concurrent

The latest version is 1.0.0, which is cross-built against Scala 2.12.x and 2.13.x.

If you're using SBT, add the following line to your build file:

```scala
libraryDependencies += "org.gfccollective" %% "gfc-concurrent" % "1.0.0"
```

For Maven and other build tools, you can visit [search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Corg.gfccollective).
(This search will also list other available libraries from the GFC Collective.)

## Contents and Example Usage

### org.gfccollective.concurrent.ScalaFutures

This object contains a bunch of sugar and little helpers that make working with scala futures a bit easier:

* Limit how long a scala Future can take by giving it a timeout Duration, after which it fails with a `java.util.concurrent.TimeoutException`
```scala
  import scala.concurrent.duration._
  import org.gfccollective.concurrent.ScalaFutures._
  val futureWithTimeout = myFuture.withTimeout(1 minute)
```
* Retry a Future until it succeeds, with or without delay:
```scala
  import scala.concurrent.duration._
  import org.gfccollective.concurrent.ScalaFutures._
  def remoteCall: Future[Response] = ???
  // Retry the remote call up to 10 times until it succeeds
  val response: Future[Response] = retry(10)(remoteCall)
```
```scala
  import scala.concurrent.duration._
  import org.gfccollective.concurrent.ScalaFutures._
  def remoteCall: Future[Response] = ???
  // Retry the remote call up to 10 times until it succeeds, with an exponential backoff,
  // starting at 10 ms and doubling each iteration until it reaches 1 second, i.e.
  // 10ms, 20ms, 40ms, 80ms, 160ms, 320ms, 640ms, 1s, 1s, 1s
  val response: Future[Response] = retryWithExponentialDelay(maxRetryTimes = 10,
                                                             maxRetryTimeout = 5 minutes fromNow,
                                                             initialDelay = 10 millis,
                                                             maxDelay = 1 second,
                                                             exponentFactor = 2) {
    remoteCall
  }
```
* Higher-order functions missing in the scala.concurrent.Future object:
```scala
  // Asynchronously tests whether a predicate holds for some of the elements of a collection of futures
  val futures: Seq[Future[String]] = ???
  ScalaFutures.exists(futures, _.contains("x"))
```
```scala
  // Asynchronously tests whether a predicate holds for all elements of a collection of futures
  val futures: Seq[Future[String]] = ???
  ScalaFutures.forall(futures, _.contains("x"))
```
* Sequential traverse that evaluates the Future function lazily and thus initiates them sequentially (one after the other) rather than in parallel as is the case with Future.traverse:
```scala
  def fetchPage(pageNo: Int): Future[Page] = ???
  val pageNumbers: Seq[Int] = 1 to 10
  val pages: Future[Seq[Page]] = ScalaFutures.traverseSequential(pageNumbers)(pageNo => fetchPage(pageNo))
```
* Enhanced fold that fails fast, as soon as a Future in the input collection fails. The "normal" scala.concurrent.Future.fold() will always take as long as the longest running Future, even if another Future has already failed. This implementation of fold will shortcut if any of the futures in the input collection fails:
```scala
  val futures: Seq[Future[String]] = ???
  val totalLength: Future[Int] = ScalaFutures.foldFast(futures)(0)((sum, str) => sum + str.length)
```
* Convert a `scala.util.Try` into a Future. If the Try is a Success, the Future is successful, if the Try is a Failure,
the Future is a failed Future with the same Exception.
```scala
  val someTry: Try[String] = Try(???)
  val someFuture: Future[String] = ScalaFutures.fromTry(someTry)
```
* Future of an empty `Option`
```scala
  val noString: Future[Option[String]] = ScalaFuture.FutureNone
```

### org.gfccollective.concurrent.SameThreadExecutionContext

`ExecutionContext` that executes an asynchronous action synchronously on the same `Thread`. This can be
useful for small code blocks that don't need to be run on a separate thread.
The object can either be used explicitly or imported implicitly like this:
```scala
  import org.gfccollective.concurrent.ScalaFutures.Implicits._
  someFuture.map(_ + 1)
```
__Note__: Using this ExecutionContext does _not_ mean that the Thread that executes this piece of code will execute the
map() function. It rather means that the Future's completion handler (the Thread that calls the registered onComplete
functions) does _not_ hand of the execution of the map() function to another thread and instead executes it synchronously.
As a result this may delay onComplete notifications for other interested parties and thus should only be used in cases
where a small piece of code needs to be executed.

### org.gfccollective.concurrent.ExecutorService / ScheduledExecutorService / AsyncScheduledExecutorService

These are scala adaptations and enhancements of `java.util.concurrent.ExecutorService` and `java.util.concurrent.ScheduledExecutorService`.
Besides offering functions to execute and schedule the execution of scala functions, the `AsyncScheduledExecutorService`
allows scheduling of asynchronous tasks, represented by a scala `Future`, that are scheduled with the same guarantees
as the (synchronous) scheduling functions. I.e. they are guaranteed to not execute concurrently. Example:
```scala
  // Have a AsyncScheduledExecutorService
  val scalaExecutor: AsyncScheduledExecutorService = ???

  // Have a function that kicks off a new asynchrouous Task
  def newTask(): Future[Any] = ???
    
  // Run this task every minute, stating in 1 minute
  import scala.concurrent.duration._
  val future = scalaExecutor.asyncScheduleAtFixedRate(1 minute, 1 minute)(newTask)
```

### org.gfccollective.concurrent.JavaConverters / JavaConversions

Implicit and explicit functions to convert java.util.concurrent.(Scheduled)ExecutorService instances to the above enhanced types.
```scala
  // Have a new ScheduledExecutorService
  val javaExecutor: ScheduledExecutorService = ???
    
  // Convert it into an AsyncScheduledExecutorService (explicit)
  import org.gfccollective.concurrent.JavaConverters._
  val scalaExecutor1: AsyncScheduledExecutorService = javaExecutor.asScala

  // Convert it into an AsyncScheduledExecutorService (implicit)
  import org.gfccollective.concurrent.JavaConversions._
  val scalaExecutor2: AsyncScheduledExecutorService = javaExecutor 
```
### org.gfccollective.concurrent.ThreadFactoryBuilder and ThreadGroupBuilder

Factories that allow the creation of a set of threads with a common name, group, daemon and other properties.
This is e.g. useful to identify background threads and make sure they do not prevent the jvm from shutting down
or for debugging/logging purposes to identify clearly what are the active threads.
```scala
  // Create a new ThreadGroup (all "with" functions are optional)
  val threadGroup = ThreadGroupBuilder().
                      withName("foo").
                      withDaemonFlag(false).
                      withParent(otherThreadGroup).
                      withMaxPriority(Thread.MIN_PRIORITY).
                      build()

  // Create a new ThreadFactory (all "with" functions are optional)
  val threadFactory = ThreadFactoryBuilder().
                          withNameFormat("bar-%s").
                          withPriority(Thread.MAX_PRIORITY).
                          withUncaughtExceptionHandler(anUncaughtExceptionHandler).
                          withThreadGroup(threadGroup).
                          withDaemonFlag(false).
                          build()
```

### Code coverage report

```
  $ sbt clean coverage test coverageReport
```

## License

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
