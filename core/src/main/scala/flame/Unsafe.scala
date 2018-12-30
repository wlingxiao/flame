package flame

import java.net.SocketAddress

import scala.concurrent.Promise

trait Unsafe {

  def bind(localAddress: SocketAddress, promise: Promise[Channel]): Unit

  def register(eventLoop: EventLoop, promise: Promise[Channel]): Unit

  def beginRead(): Unit

  def read(): Unit

  /**
    * Schedules a write operation.
    */
  def write(msg: Any, promise: Promise[Channel]): Unit

  /**
    * Flush out all write operations scheduled via [[write()]]
    */
  def flush(): Unit

  def close(promise: Promise[Channel]): Unit

  def deregister(promise: Promise[Channel]): Unit
}
