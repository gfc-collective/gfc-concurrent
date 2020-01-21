package org.gfccollective.concurrent

import java.util.concurrent.{ExecutorService => JExecutorService, ScheduledExecutorService => JScheduledExecutorService}

/**
 * Implicit conversions from java.util.concurrent.ExecutorService and java.util.concurrent.ScheduledExecutorService to
 * org.gfccollective.concurrent.ExecutorService and org.gfccollective.concurrent.AsyncScheduledExecutorService
 */
object JavaConversions {
  import scala.language.implicitConversions

  implicit def asScalaExecutorService(jes: JExecutorService): ExecutorService = new JExecutorServiceWrapper {
    override val executorService = jes
  }

  implicit def asScalaAsyncScheduledExecutorService(jses: JScheduledExecutorService): AsyncScheduledExecutorService = new JScheduledExecutorServiceWrapper {
    override val executorService = jses
  }
}
