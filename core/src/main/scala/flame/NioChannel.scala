package flame

import java.net.SocketAddress
import java.nio.channels.SocketChannel

import scala.concurrent.Promise

private[flame] case class NioChannel(override val pipeline: Pipeline,
                                     socket: SocketChannel,
                                     loop: SelectorLoop) extends Channel(null, null) {
  override def isActive: Boolean = ???

  override def close(promise: Promise[Channel]): Unit = ???

  override def unsafe: Unsafe = ???
}
