package flame

import java.nio.ByteBuffer
import java.nio.channels.{ClosedChannelException, SelectionKey, Selector, SocketChannel}
import java.util.concurrent.{Executor, LinkedBlockingQueue}
import java.util.{Set => JSet}

import flame.logging.Logging

import scala.annotation.tailrec
import scala.concurrent.Promise

class SelectorLoop(
                    executor: Executor,
                    bufferSize: Int)
  extends Runnable with Logging {

  @volatile
  private var isClose = false

  private val registerTaskQueue = new LinkedBlockingQueue[Runnable]()

  private val selector: Selector = Selector.open()

  private var thread: Thread = _

  def register(channel: Channel): Unit = {
    registerTaskQueue.offer(new RegisterTask(channel))
    executor.execute(this)
    selector.wakeup()
  }

  override def run(): Unit = {
    thread = Thread.currentThread()
    while (!isClose) {
      val selected = selector.select(500)
      processRegisterTaskQueue()
      if (selected > 0) {
        processSelectedKeys(selector.selectedKeys())
      }
    }
  }

  private def processSelectedKeys(keys: JSet[SelectionKey]): Unit = {
    val it = keys.iterator()
    while (it.hasNext) {
      val k = it.next()
      it.remove()
      if (k.isReadable) {
        read(k)
      } else if (k.isWritable) {
        val attach = k.attachment()
        if (attach != null) {
          val (channel, buf, promise) = k.attachment().asInstanceOf[(NIO1Channel, ByteBuffer, Promise[Int])]
          write(channel, buf, promise)
        }
      }
    }
  }

  private def read(k: SelectionKey): Unit = {
    val buf = ByteBuffer.allocateDirect(bufferSize)
    val channel = k.channel().asInstanceOf[SocketChannel]
    val sc = k.attachment().asInstanceOf[Channel]
    val r = try {
      val rr = channel.read(buf)
      buf.flip()
      sc.pipeline.sendReceived(buf)
      rr
    } catch {
      case ignore: ClosedChannelException =>
        log.info(ignore.getMessage)
        -1
    }
    if (r < 0) {
      k.cancel()
      channel.close()
    }
  }

  def write(channel: NIO1Channel, buf: ByteBuffer, promise: Promise[Int]): Unit = {
    val remain = buf.remaining()
    val ret = channel.socket.write(buf)
    if (ret < remain) {
      channel.socket.register(selector, SelectionKey.OP_WRITE, (channel, buf, promise))
    } else {
      clearOpWrite(channel)
    }
  }

  private def setOpWrite(channel: NIO1Channel): Unit = {

  }

  private def clearOpWrite(channel: NIO1Channel): Unit = {
    val key = channel.socket.keyFor(selector)
    var interestOps = SelectionKey.OP_READ | SelectionKey.OP_WRITE
    interestOps &= ~SelectionKey.OP_WRITE
    key.interestOps(interestOps)
    key.attach(channel)
  }

  @tailrec
  private def processRegisterTaskQueue(): Unit = {
    val task = registerTaskQueue.poll()
    if (task == null) {
      ()
    } else {
      task.run()
      processRegisterTaskQueue()
    }
  }

  private class RegisterTask(channel: Channel) extends Runnable {
    override def run(): Unit = {
      channel.socket.register(selector, SelectionKey.OP_READ, channel)
    }
  }

  def close(): Unit = {
    isClose = true
    log.info(s"shutting down SelectorLoop ${if (thread ne null) thread.getName else "unbind"}")
    if (selector ne null) {
      selector.close()
      selector.wakeup()
    }
  }

}
