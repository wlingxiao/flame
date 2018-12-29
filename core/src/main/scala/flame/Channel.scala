package flame

import java.net.SocketAddress
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectableChannel, SelectionKey, ServerSocketChannel, SocketChannel}

import scala.concurrent.Promise

abstract class Channel(parent: Channel, ch: SelectableChannel) {

  private val _pipeline = newPipeline

  private var _eventLoop: EventLoop = _

  def pipeline: Pipeline = _pipeline

  def eventLoop: EventLoop = {
    _eventLoop
  }

  private[flame] def register(el: EventLoop): Unit = {
    _eventLoop = el
  }

  def isOpen: Boolean = {
    ch.isOpen
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

  def unsafe: Unsafe

}

class NioSocketChannel(ch: SocketChannel = SelectorProvider.provider.openSocketChannel()) extends Channel(null, ch) {

  override def isActive: Boolean = {
    ch.isOpen && ch.isConnected
  }

  override def close(promise: Promise[Channel]): Unit = {
    ch.close()
  }

  override def bind(localAddress: SocketAddress): Unit = {
    ch.bind(localAddress)
  }

  override def unsafe: Unsafe = ???
}

class NioServerSocketChannel(ch: ServerSocketChannel = SelectorProvider.provider.openServerSocketChannel()) extends Channel(null, ch) {

  private val readInterestOp = SelectionKey.OP_ACCEPT

  ch.configureBlocking(false)

  @volatile
  protected var selectionKey: SelectionKey = _

  override def isActive: Boolean = {
    ch.socket().isBound
  }

  def close(promise: Promise[Channel]): Unit = {
    ch.close()
  }

  def doBind(localAddress: SocketAddress): Unit = {
    ch.bind(localAddress)
  }

  override def unsafe: Unsafe = new Unsafe {
    override def bind(localAddress: SocketAddress): Unit = {
      doBind(localAddress)
    }

    override def register(eventLoop: EventLoop): Unit = {
      NioServerSocketChannel.this.register(eventLoop)
      if (eventLoop.inEventLoop) {
        selectionKey = ch.register(eventLoop.asInstanceOf[NioEventLoop].selector, 0, this)
      } else {
        eventLoop.execute { () =>
          selectionKey = ch.register(eventLoop.asInstanceOf[NioEventLoop].selector, 0, this)
          pipeline.fireChannelActive()
        }
      }
    }

    override def beginRead(): Unit = {
      assert(eventLoop.inEventLoop)
      doBeginRead()
    }
  }

  private def doBeginRead(): Unit = {
    if (!selectionKey.isValid) {
      return
    }
    val interestOps = selectionKey.interestOps
    if ((interestOps & readInterestOp) == 0) {
      selectionKey.interestOps(interestOps | readInterestOp)
    }
  }

}