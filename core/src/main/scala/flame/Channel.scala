package flame

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectableChannel, SelectionKey, ServerSocketChannel, SocketChannel}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.util.Try

abstract class Channel(parent: Channel) {

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

  def isActive: Boolean

  def close(promise: Promise[Channel]): Unit

  protected def newPipeline: Pipeline = {
    Pipeline(this)
  }

  def bind(localAddress: SocketAddress): Unit = {
    pipeline.bind(localAddress)
  }

  def read(): Channel = {
    pipeline.read()
    this
  }

  def write(msg: Any): Future[Channel] = {
    pipeline.write(msg)
  }

  def flush(): Channel = {
    pipeline.flush()
    this
  }

  def unsafe: Unsafe = new AbstractUnsafe

  class AbstractUnsafe extends Unsafe {

    def bind(localAddress: SocketAddress, promise: Promise[Channel]): Unit = {
      promise.complete(Try {
        doBind(localAddress)
        Channel.this
      })
    }

    def register(eventLoop: EventLoop, promise: Promise[Channel]): Unit = {
      Channel.this.register(eventLoop)
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
        selectionKey = javaChannel.register(eventLoop.asInstanceOf[NioEventLoop].selector, 0, Channel.this)
        pipeline.sendChannelRegistered()
        pipeline.sendChannelActive()
        Channel.this
      })
    }

    def beginRead(): Unit = {
      assert(eventLoop.inEventLoop)
      doBeginRead()
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

    def write(msg: Any, promise: Promise[Channel]): Unit = ???

    def flush(): Unit = ???
  }

  protected def doBind(localAddress: SocketAddress): Unit

  protected def doBeginRead(): Unit = {
    if (!selectionKey.isValid) {
      return
    }
    val interestOps = selectionKey.interestOps
    if ((interestOps & readInterestOp) == 0) {
      selectionKey.interestOps(interestOps | readInterestOp)
    }
  }

  protected def doReadMessages(buf: ArrayBuffer[Any]): Int
}

class NioSocketChannel(parent: Channel, ch: SocketChannel) extends Channel(null) {

  ch.configureBlocking(false)

  @volatile
  protected var selectionKey: SelectionKey = _

  override def isActive: Boolean = {
    ch.isOpen && ch.isConnected
  }

  override def javaChannel: SelectableChannel = ch

  override def close(promise: Promise[Channel]): Unit = {
    ch.close()
  }

  override def bind(localAddress: SocketAddress): Unit = {
    ch.bind(localAddress)
  }

  val readInterestOp: Int = SelectionKey.OP_READ

  override def doBind(localAddress: SocketAddress): Unit = ???

  override def doReadMessages(buf: ArrayBuffer[Any]): Int = {
    val readBuf = ArrayBuffer[Any]()
    do {
      val buf = ByteBuffer.allocateDirect(1024)
      ch.read(buf)
      readBuf += buf
    } while (false)
    readBuf.foreach { buf =>
      pipeline.sendChannelRead(buf)
    }
    readBuf.clear()
    pipeline.sendChannelReadComplete()
    1
  }
}

class NioServerSocketChannel extends Channel(null) {
  val ch = SelectorProvider.provider.openServerSocketChannel()
  ch.configureBlocking(false)

  val javaChannel: ServerSocketChannel = {
    ch
  }

  @volatile
  protected var selectionKey: SelectionKey = _

  override def isActive: Boolean = {
    ch.socket().isBound
  }

  def close(promise: Promise[Channel]): Unit = {
    ch.close()
  }

  def doBind(localAddress: SocketAddress): Unit = {
    javaChannel.bind(localAddress)
  }

  def doReadMessages(buf: ArrayBuffer[Any]): Int = {
    val socketChannel = ch.accept()
    buf += new NioSocketChannel(this, socketChannel)
    1
  }

  val readInterestOp: Int = SelectionKey.OP_ACCEPT
}