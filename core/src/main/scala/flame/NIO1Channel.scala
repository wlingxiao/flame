package flame

import java.net.SocketAddress
import java.nio.channels.SocketChannel

private[flame] case class NIO1Channel(pipeline: Pipeline,
                                      socket: SocketChannel,
                                      loop: SelectorLoop) extends Channel {
  override def local: SocketAddress = socket.getLocalAddress

  override def remote: SocketAddress = socket.getRemoteAddress

  override def toString: String = {
    s"[local=$local remote=$remote]"
  }
}
