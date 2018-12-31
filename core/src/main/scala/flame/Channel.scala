package flame

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectableChannel, SelectionKey, ServerSocketChannel, SocketChannel}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.util.Try

trait Channel extends OutboundInvoker {
  def parent: Channel

  def eventLoop: EventLoop

  def isOpen: Boolean

  def isActive: Boolean

  def pipeline: Pipeline

  private[flame] def unsafe: Unsafe
}

trait NioChannel extends Channel {

  private val _pipeline = newPipeline

  private var _eventLoop: EventLoop = _

  @volatile
  private var selectionKey: SelectionKey = _

  def pipeline: Pipeline = _pipeline

  def eventLoop: EventLoop = {
    _eventLoop
  }

  private[flame] def register(el: EventLoop): Unit = {
    _eventLoop = el
  }

  def javaChannel: SelectableChannel

  def readInterestOp: Int

  def isOpen: Boolean = {
    javaChannel.isOpen
  }

  protected def newPipeline: Pipeline = {
    Pipeline(this)
  }

  def bind(localAddress: SocketAddress): Future[Channel] = {
    pipeline.bind(localAddress)
  }

  def bind(socketAddress: SocketAddress, promise: Promise[Channel]): Future[Channel] = {
    pipeline.bind(socketAddress, promise)
  }

  def close(): Future[Channel] = {
    pipeline.close()
  }

  def close(promise: Promise[Channel]): Future[Channel] = {
    pipeline.close(promise)
  }

  def read(): this.type = {
    pipeline.read()
    this
  }

  def write(msg: Any): Future[Channel] = {
    pipeline.write(msg)
  }

  def write(msg: Any, promise: Promise[Channel]): Future[Channel] = {
    pipeline.write(msg)
  }

  def flush(): this.type = {
    pipeline.flush()
    this
  }

  def deregister(promise: Promise[Channel]): Future[Channel] = {
    pipeline.deregister(promise)
  }

  def deregister(): Future[Channel] = {
    pipeline.deregister()
  }

  def unsafe: Unsafe

  protected abstract class AbstractUnsafe extends Unsafe {

    private val writeBuffer = ByteBuffer.allocate(1024)

    private def assertInEventLoop(): Unit = {
      assert(eventLoop.inEventLoop)
    }

    def bind(localAddress: SocketAddress, promise: Promise[Channel]): Unit = {
      assertInEventLoop()
      promise.complete(Try {
        doBind(localAddress)
        NioChannel.this
      })
    }

    protected def doBind(localAddress: SocketAddress): Unit

    def register(eventLoop: EventLoop, promise: Promise[Channel]): Unit = {
      NioChannel.this.register(eventLoop)
      if (eventLoop.inEventLoop) {
        register(promise)
      } else {
        eventLoop.execute { () =>
          register(promise)
        }
      }
    }

    private def register(promise: Promise[Channel]): Unit = {
      promise.complete(Try {
        selectionKey = javaChannel.register(eventLoop.asInstanceOf[NioEventLoop].selector, 0, NioChannel.this)
        pipeline.sendChannelRegistered()
        pipeline.sendChannelActive()
        NioChannel.this
      })
    }

    def beginRead(): Unit = {
      assertInEventLoop()
      doBeginRead()
    }

    protected def doBeginRead(): Unit = {
      if (!selectionKey.isValid) {
        return
      }
      val interestOps = selectionKey.interestOps
      if ((interestOps & readInterestOp) == 0) {
        selectionKey.interestOps(interestOps | readInterestOp)
      }
    }

    def read(): Unit

    def write(msg: Any, promise: Promise[Channel]): Unit = {
      assertInEventLoop()
      msg match {
        case buf: ByteBuffer =>
          writeBuffer.put(buf)
        case _ => throw new UnsupportedOperationException
      }
    }

    def flush(): Unit = {
      assertInEventLoop()
      writeBuffer.flip()
      doFlush(writeBuffer)
      writeBuffer.clear()
    }

    def doFlush(out: ByteBuffer): Unit

    def close(promise: Promise[Channel]): Unit = {
      assertInEventLoop()
      promise.complete(Try {
        javaChannel.close()
        NioChannel.this
      })
    }

    override def deregister(promise: Promise[Channel]): Unit = {
      assertInEventLoop()
      eventLoop.execute { () =>
        doDeregister()
      }
    }

    protected def doDeregister(): Unit = {
      eventLoop.asInstanceOf[NioEventLoop].cancel(selectionKey)
    }
  }

}

class NioSocketChannel(val parent: Channel, ch: SocketChannel) extends NioChannel {

  private val bufferSize = 2

  ch.configureBlocking(false)

  @volatile
  protected var selectionKey: SelectionKey = _

  def isActive: Boolean = {
    ch.isOpen && ch.isConnected
  }

  def javaChannel: SelectableChannel = ch

  val readInterestOp: Int = SelectionKey.OP_READ

  def unsafe: Unsafe = new NioUnsafe

  class NioUnsafe extends AbstractUnsafe {
    def doBind(localAddress: SocketAddress): Unit = {
      javaChannel.asInstanceOf[SocketChannel].bind(localAddress)
    }

    def doFlush(out: ByteBuffer): Unit = {
      ch.write(out)
    }

    def read(): Unit = {
      var count: Int = -1
      do {
        val buf = ByteBuffer.allocateDirect(bufferSize)
        count = ch.read(buf)
        if (count != -1 && count != 0) {
          buf.flip()
          pipeline.sendChannelRead(buf)
        }
      } while (count != -1 && count != 0)
      pipeline.sendChannelReadComplete()
      if (count < 0) {
        unsafe.close(Promise[Channel]())
      }
    }
  }

}

class NioServerSocketChannel extends NioChannel {

  override def parent: Channel = null

  private val ch = SelectorProvider.provider.openServerSocketChannel()
  ch.configureBlocking(false)

  val javaChannel: ServerSocketChannel = {
    ch
  }

  @volatile
  protected var selectionKey: SelectionKey = _

  override def isActive: Boolean = {
    ch.socket().isBound
  }

  val readInterestOp: Int = SelectionKey.OP_ACCEPT

  override def unsafe: Unsafe = new NioServerUnsafe

  private class NioServerUnsafe extends AbstractUnsafe {

    def doBind(localAddress: SocketAddress): Unit = {
      javaChannel.bind(localAddress)
    }

    def read(): Unit = {
      assert(eventLoop.inEventLoop)
      val readBuf = ArrayBuffer[Any]()
      do {
        doReadMessages(readBuf)
      } while (false)
      readBuf.foreach { buf =>
        pipeline.sendChannelRead(buf)
      }
      readBuf.clear()
      pipeline.sendChannelReadComplete()
    }

    def doReadMessages(buf: ArrayBuffer[Any]): Int = {
      val socketChannel = ch.accept()
      buf += new NioSocketChannel(NioServerSocketChannel.this, socketChannel)
      1
    }

    def doFlush(out: ByteBuffer): Unit = ???
  }

}