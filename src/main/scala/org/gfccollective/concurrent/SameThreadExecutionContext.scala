package org.gfccollective.concurrent

import scala.concurrent.ExecutionContext
import org.gfccollective.logging.Loggable

/**
 * For small code blocks that don't need to be run on a separate thread.
 */
object SameThreadExecutionContext extends ExecutionContext with Loggable {
  override def execute(runnable: Runnable): Unit = runnable.run
  override def reportFailure(t: Throwable): Unit = error(t.getMessage, t)
}
