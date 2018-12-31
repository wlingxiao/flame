package flame

import java.net.SocketAddress

import scala.concurrent.{Future, Promise}

trait OutboundInvoker {

  def bind(localAddress: SocketAddress): Future[Channel]

  def bind(socketAddress: SocketAddress, promise: Promise[Channel]): Future[Channel]

  // connect
  // disconnect

  def close(): Future[Channel]

  def close(promise: Promise[Channel]): Future[Channel]

  def write(msg: Any): Future[Channel]

  def write(msg: Any, promise: Promise[Channel]): Future[Channel]

  def deregister(): Future[Channel]

  def deregister(promise: Promise[Channel]): Future[Channel]

  def read(): this.type

  def flush(): this.type

}
