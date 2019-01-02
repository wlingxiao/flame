package flame

import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectionKey, Selector}
import java.util.concurrent.Executor
import java.util.{Set => JSet}

import scala.concurrent.{Future, Promise}


trait EventLoop extends EventLoopGroup with EventExecutor {

  def parent: EventLoopGroup

}

abstract class SingleThreadEventLoop extends SingleThreadEventExecutor with EventLoop {
  override def next: EventLoop = {
    super.next.asInstanceOf[EventLoop]
  }
}

class NioEventLoop(val parent: EventLoopGroup,
                   selectorProvider: SelectorProvider,
                   val executor: Executor) extends SingleThreadEventLoop {
  val selector: Selector = selectorProvider.openSelector()

  def register(channel: Channel): Future[Channel] = {
    register(channel, Promise[Channel]())
  }

  def register(channel: Channel, promise: Promise[Channel]): Future[Channel] = {
    channel.unsafe.register(this, promise)
    promise.future
  }

  def run(): Unit = {
    while (true) {
      select()
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