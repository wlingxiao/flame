package flame

import java.nio.channels._
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

import flame.logging.Logging

private[flame] class Acceptor(
                               executor: Executor,
                               serverChannel: ServerSocketChannel,
                               initializer: Initializer,
                               poolSize: Int,
                               bufferSize: Int)
  extends Thread with Logging {

  private val selector = Selector.open()

  serverChannel.register(selector, SelectionKey.OP_ACCEPT)

  private val loops = Array.fill(poolSize) {
    new SelectorLoop(executor, bufferSize)
  }

  private val loopIndex = new AtomicInteger

  @volatile
  private var isClose = false

  override def run(): Unit = {
    while (!isClose) {
      try {
        selector.select(1000)
        val client = serverChannel.accept()
        if (client != null) {
          client.configureBlocking(false)
          val loop = nextLoop()
          val pipeline: Pipeline = ???
          val channel = NioChannel(pipeline, client, loop)
          initializer.init(channel)
          loop.register(channel)
        }
      } catch {
        case e: ClosedChannelException =>
          log.info(e.getMessage)
          isClose = true
        case e: ClosedSelectorException =>
          log.info(e.getMessage)
          isClose = true
      }
    }
  }

  private def nextLoop(): SelectorLoop = {
    loops(Math.abs(loopIndex.getAndIncrement() % loops.length))
  }

  def close(): Unit = {
    selector.close()
    loops.foreach(_.close())
    isClose = false
  }
}
