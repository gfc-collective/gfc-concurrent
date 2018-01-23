package com.gilt.gfc.concurrent

import java.util.concurrent.{Executors, ScheduledFuture, ScheduledExecutorService => JScheduledExecutorService}
import java.util.concurrent.atomic.AtomicReference

import com.gilt.gfc.logging.Loggable

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


/** Batches multiple 'one-at-a-time' calls into a single batch
  * e.g. to reduce costs of Kineis RPC API calls.
  */
trait Batcher[R] {

  /** Add a single record to batch. */
  def add(r: R): Unit

  /** Flush all outstanding records. */
  def flush(): Unit

  /** Flush outstanding records and shutdown background tasks.
    * Batcher instance can not be used after this call.
    */
  def shutdown(): Unit
}


object Batcher {

  /** Creates a batcher.
    * This instance should be thread-safe.
    *
    * @param name                    for logging
    * @param maxOutstandingCount     a batch will be submitted when record count reaches this number, it is assumed to be relatively small, see implementation of flush() for details
    * @param maxOutstandingDuration  we'll submit a batch with this periodicity regardless of the outstanding record count
    * @param submitBatch             what to do with a batch of records, hopefully an asynchronous operation (fast call), must be thread-safe
    * @param executor                where to run background task, safe to use global if submitBatch is non-blocking
    * @tparam R                      record type parameter
    * @return                        constructed batcher
    */
  def apply[R]( name: String
              , maxOutstandingCount: Int
              , maxOutstandingDuration: FiniteDuration
              , executor: JScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
             )( submitBatch: (Iterable[R]) => Unit
             ): Batcher[R] = {
    require(maxOutstandingCount > 0, s"maxOutstandingCount must be >0")

    new BatcherImpl[R](
      name
    , maxOutstandingCount
    , maxOutstandingDuration
    , submitBatch
    , executor
    )
  }
}


private[concurrent] final
class BatcherImpl[R] (
  name: String
, maxOutstandingCount: Int
, maxOutstandingDuration: FiniteDuration
, submitBatch: (Iterable[R]) => Unit
, executor: JScheduledExecutorService
) extends Batcher[R]
     with Loggable {
  import JavaConverters._

  private[this]
  val emptyBatch = (0, Vector.empty[R])

  private[this]
  val currentBatch = new AtomicReference(emptyBatch)

  @volatile
  private[this]
  var isRunning = true

  @volatile
  private[this]
  var lastSubmit = System.currentTimeMillis()

  // Flush buffer periodically
  @volatile
  private[this]
  var task = executor.asScala.schedule(maxOutstandingDuration)(schedule())

  private def schedule(): Unit = {
    if (isRunning) {
      import scala.concurrent.duration._
      val elapsed = (System.currentTimeMillis() - lastSubmit) millis
      val flushNow = elapsed >= maxOutstandingDuration
      val nextRun = if (flushNow) maxOutstandingDuration else maxOutstandingDuration - elapsed
      task = executor.asScala.schedule(nextRun)(schedule())
      if (flushNow) {
        flush()
      }
    }
  }


  /** Add a single record to batch. */
  @tailrec
  override
  def add(r: R): Unit = {
    require(isRunning, s"${name} batcher is shutting down, can not add any more records.")

    val b@(batchSize, records) = currentBatch.get()
    val b1@(newBatchSize, newRecords) = (batchSize+1, records :+ r)

    if (newBatchSize >= maxOutstandingCount) {
      if (currentBatch.compareAndSet(b, emptyBatch)) {
        safeSubmitBatch(newRecords)
      } else {
        add(r) // retry
      }
    } else {
      if (!currentBatch.compareAndSet(b, b1)) {
        add(r) // retry
      }
    }
  }

  /** Flush all outstanding records. */
  @tailrec
  override
  def flush(): Unit = {
    val b@(_, records) = currentBatch.get()
    if (currentBatch.compareAndSet(b, emptyBatch)) {
      safeSubmitBatch(records)
    } else {
      flush() // retry
    }
  }

  override
  def shutdown(): Unit = {
    isRunning = false
    task.cancel(true)
    flush()
  }

  private[this]
  def safeSubmitBatch(records: Vector[R]): Unit = {
    if (!records.isEmpty) {
      lastSubmit = System.currentTimeMillis()
      try {
        submitBatch(records)
      } catch {
        case NonFatal(e) =>
          error(s"Failed to flush ${name} batch: ${e.getMessage}", e)
      }
    }
  }
}
