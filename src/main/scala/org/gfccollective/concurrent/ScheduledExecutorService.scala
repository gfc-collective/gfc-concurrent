package org.gfccollective.concurrent

import java.util.concurrent.{ScheduledExecutorService => JScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration

/**
 * Scala adaptations of j.u.c.ScheduledExecutorService.
 */
trait ScheduledExecutorService extends JScheduledExecutorService with ExecutorService {
  def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)(f: => Unit): ScheduledFuture[_]

  def scheduleAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)(f: => Unit): ScheduledFuture[_]

  def schedule[V](delay: FiniteDuration)(f: => V): ScheduledFuture[V]
}
