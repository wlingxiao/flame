package flame

import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

trait EventLoopGroup {

  def next: EventLoop

  def register(channel: Channel): Unit

}

class NioEventLoopGroup(nThreads: Int) extends EventLoopGroup {

  private val selectorProvider = SelectorProvider.provider()

  private val loopIndex = new AtomicInteger

  private val executor: Executor = new Executor {
    override def execute(command: Runnable): Unit = {
      new Thread(command).start()
    }
  }

  private val children = Array.fill(nThreads)(newChild(executor))

  override def next: EventLoop = {
    nextLoop
  }

  private def nextLoop: EventLoop = {
    children(Math.abs(loopIndex.getAndIncrement() % children.length))
  }

  override def register(channel: Channel): Unit = {
    next.register(channel)
  }

  private def newChild(executor: Executor): EventLoop = {
    new NioEventLoop(this, selectorProvider, executor)
  }

}