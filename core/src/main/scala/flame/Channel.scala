package flame

import java.net.SocketAddress
import java.nio.channels.SocketChannel

trait Channel {

  def pipeline: Pipeline

  def socket: SocketChannel

  def local: SocketAddress

  def remote: SocketAddress

  def close(): Unit = {
    socket.close()
  }

}
