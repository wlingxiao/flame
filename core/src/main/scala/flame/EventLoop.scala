package flame

import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectionKey, Selector}
import java.util.concurrent.{Executor, LinkedBlockingQueue}
import java.util.{Set => JSet}

import scala.annotation.tailrec


trait EventLoop extends EventExecutor with EventLoopGroup {

  def parent: EventLoopGroup

  def run(): Unit

  def inEventLoop: Boolean
}

class NioEventLoop(val parent: EventLoopGroup,
                   selectorProvider: SelectorProvider, executor: Executor) extends EventLoop {

  private val taskQueue = new LinkedBlockingQueue[Runnable](10)

  val selector: Selector = selectorProvider.openSelector()

  @volatile
  private var thread: Thread = _

  override def next: EventLoop = {
    this
  }

  override def register(channel: Channel): Unit = {
    channel.unsafe.register(this)
  }


  override def inEventLoop: Boolean = {
    Thread.currentThread() == thread
  }

  override def execute(task: Runnable): Unit = {
    taskQueue.add(task)
    if (!inEventLoop) {
      executor.execute { () =>
        thread = Thread.currentThread()
        run()
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
    println(selectedKeys.size())
  }

}