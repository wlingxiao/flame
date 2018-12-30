package flame

import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.{Executor, ThreadFactory}
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{Future, Promise}

trait EventLoopGroup {

  def next: EventLoop

  def register(channel: Channel): Future[Channel]

  def register(channel: Channel, promise: Promise[Channel]): Future[Channel]
}

class NioEventLoopGroup(nThreads: Int) extends EventLoopGroup {

  private val selectorProvider = SelectorProvider.provider()

  private val loopIndex = new AtomicInteger

  private val executor: Executor = new ThreadPerTaskExecutor(defaultThreadFactory)

  private val children = Array.fill(nThreads)(newChild(executor))

  override def next: EventLoop = {
    nextLoop
  }

  private def nextLoop: EventLoop = {
    children(Math.abs(loopIndex.getAndIncrement() % children.length))
  }

  override def register(channel: Channel): Future[Channel] = {
    next.register(channel)
  }


  override def register(channel: Channel, promise: Promise[Channel]): Future[Channel] = {
    next.register(channel, promise)
  }

  private def newChild(executor: Executor): EventLoop = {
    new NioEventLoop(this, selectorProvider, executor)
  }

  private def defaultThreadFactory: ThreadFactory = {
    new ThreadFactoryImpl(getClass.getSimpleName)
  }

}