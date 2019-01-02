package flame

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.{Executor, LinkedBlockingQueue}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.Try

trait EventExecutor extends EventExecutorGroup {

  def inEventLoop: Boolean

  def parent: EventExecutorGroup

}

abstract class SingleThreadEventExecutor extends EventExecutor {

  protected def executor: Executor

  private[this] val taskQueue = new LinkedBlockingQueue[Runnable](10)

  private val stateUpdater = AtomicIntegerFieldUpdater.newUpdater(classOf[SingleThreadEventExecutor], "state")

  @volatile
  private var state = 1

  @volatile
  private var thread: Thread = _

  def next: EventExecutor = this

  def inEventLoop: Boolean = {
    Thread.currentThread() == thread
  }

  def apply[T](task: => T): Future[T] = {
    val promise = Promise[T]()
    execute { () => promise.tryComplete(Try(task)) }
    promise.future
  }

  private def execute(task: Runnable): Unit = {
    taskQueue.add(task)
    if (!inEventLoop) {
      startThread()
    }
  }

  private def startThread(): Unit = {
    if (state == 1) {
      if (stateUpdater.compareAndSet(this, 1, 2)) {
        assert(thread == null)
        executor.execute { () =>
          thread = Thread.currentThread()
          run()
        }
      }
    }
  }

  protected def run(): Unit

  @tailrec
  protected final def runAllTask(): Unit = {
    assert(inEventLoop)
    val task = taskQueue.poll()
    if (task != null) {
      task.run()
      runAllTask()
    }
  }

}