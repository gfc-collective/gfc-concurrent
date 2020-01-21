package org.gfccollective.concurrent

/**
 * Simple Builder for java.lang.ThreadGroup
 *
 * By default groups have the daemon flag set to false, as such a group will destroy itself
 * if it ever becomes empty after having at least one thread, for example if the factory is
 * used to create threads for an infrequently-used cached thread pool.
 *
 * @author Gregor Heine
 * @since 09/Feb/2015 12:01
 */
object ThreadGroupBuilder {
  def apply(): ThreadGroupBuilder = ThreadGroupBuilder(None, false, None, None)

  // The ThreadGroup of the current Thread or the SecurityManager if one exists
  private[concurrent] def currentThreadGroup(): ThreadGroup = {
    val secMgr = Option(System.getSecurityManager)
    secMgr.fold(Thread.currentThread.getThreadGroup)(_.getThreadGroup)
  }
}

case class ThreadGroupBuilder private (private val name: Option[String],
                                       private val daemon: Boolean,
                                       private val parent: Option[ThreadGroup],
                                       private val maxPriority: Option[Int]) {
  def withName(name: String): ThreadGroupBuilder = copy(name = Some(name))

  def withDaemonFlag(isDaemon: Boolean): ThreadGroupBuilder = copy(daemon = isDaemon)

  def withParent(parent: ThreadGroup): ThreadGroupBuilder = copy(parent = Some(parent))

  def withMaxPriority(maxPriority: Int): ThreadGroupBuilder = copy(maxPriority = Some(maxPriority))

  def build(): ThreadGroup = {
    val group = new ThreadGroup(parent.getOrElse(ThreadGroupBuilder.currentThreadGroup), name.orNull)
    group.setDaemon(daemon)
    maxPriority.foreach(group.setMaxPriority)
    group
  }
}
