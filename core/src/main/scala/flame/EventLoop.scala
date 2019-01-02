package flame

import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectionKey, Selector}
import java.util.concurrent.atomic.{AtomicIntegerFieldUpdater, AtomicReferenceFieldUpdater}
import java.util.concurrent.{Executor, LinkedBlockingQueue}
import java.util.{Set => JSet}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.Try


trait EventLoop extends EventExecutor with EventLoopGroup {

  def parent: EventLoopGroup

  def run(): Unit
}

class NioEventLoop(val parent: EventLoopGroup,
                   selectorProvider: SelectorProvider, executor: Executor) extends EventLoop {

  private val stateUpdater = AtomicIntegerFieldUpdater.newUpdater(classOf[NioEventLoop], "state")

  @volatile
  private var state = 1

  private val taskQueue = new LinkedBlockingQueue[Runnable](10)

  val selector: Selector = selectorProvider.openSelector()

  @volatile
  private var thread: Thread = _

  override def next: EventLoop = {
    this
  }

  override def register(channel: Channel): Future[Channel] = {
    register(channel, Promise[Channel]())
  }

  override def register(channel: Channel, promise: Promise[Channel]): Future[Channel] = {
    channel.unsafe.register(this, promise)
    promise.future
  }

  def inEventLoop: Boolean = {
    Thread.currentThread() == thread
  }


  def apply[T](task: => T): Future[T] = {
    val promise = Promise[T]()
    execute { () => promise.tryComplete(Try(task)) }
    promise.future
  }

  def execute(task: Runnable): Unit = {
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

  def run(): Unit = {
    while (true) {
      select()
    }
  }

  @tailrec
  private def runAllTask(): Unit = {
    assert(inEventLoop)
    val task = taskQueue.poll()
    if (task != null) {
      task.run()
      runAllTask()
    }
  }

  private def select(): Unit = {
    selector.select(1000L)
    processSelectedKeys()
    runAllTask()
  }

  private def processSelectedKeys(): Unit = {
    processSelectedKeysPlain(selector.selectedKeys())
  }

  private def processSelectedKeysPlain(selectedKeys: JSet[SelectionKey]): Unit = {
    val it = selectedKeys.iterator()
    while (it.hasNext) {
      val key = it.next()
      it.remove()
      val serverChannel = key.attachment().asInstanceOf[Channel]
      val readyOps = key.readyOps()
      if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
        serverChannel.unsafe.read()
      }
    }
  }

  def cancel(key: SelectionKey): Unit = {
    key.cancel()
  }

}