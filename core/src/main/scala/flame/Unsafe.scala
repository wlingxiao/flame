package flame

import java.net.SocketAddress

trait Unsafe {

  def bind(localAddress: SocketAddress): Unit

  def register(eventLoop: EventLoop): Unit

  def beginRead(): Unit

}
