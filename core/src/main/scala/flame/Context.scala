package flame

import java.net.SocketAddress

import scala.concurrent.{Future, Promise}

trait Context {

  def channel: Channel = {
    pipeline.channel
  }

  def handler: Handler

  def pipeline: Pipeline

  def name: String

  def executor: EventExecutor

  def bind(socketAddress: SocketAddress): Future[Channel]

  def bind(socketAddress: SocketAddress, promise: Promise[Channel]): Future[Channel]

  def read(): Context

  def write(msg: Any): Future[Channel]

  def write(msg: Any, promise: Promise[Channel]): Future[Channel]

  def flush(): Context

  def send(ev: Event): Unit

}