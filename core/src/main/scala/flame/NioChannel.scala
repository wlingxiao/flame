package flame

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{SelectableChannel, SocketChannel}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Promise

private[flame] case class NioChannel(override val pipeline: Pipeline,
                                     socket: SocketChannel,
                                     loop: SelectorLoop) extends Channel(null) {
  override def isActive: Boolean = ???

  override def close(promise: Promise[Channel]): Unit = ???

  override def unsafe: Unsafe = ???

  override def javaChannel: SelectableChannel = ???

  override def readInterestOp: Int = ???

  override protected def doBind(localAddress: SocketAddress): Unit = ???

  override protected def doWrite(out: ByteBuffer): Unit = ???

  override protected def doRead(): Unit = ???
}
