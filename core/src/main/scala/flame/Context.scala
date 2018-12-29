package flame

import java.net.SocketAddress

import scala.concurrent.{Future, Promise}

trait Context {

  def channel: Channel = {
    pipeline.channel
  }

  def handler: Handler

  def pipeline: Pipeline

  def bind(socketAddress: SocketAddress): Future[Channel]

  def bind(socketAddress: SocketAddress, promise: Promise[Channel]): Future[Channel]

  def read(): Context

  def send(ev: Event): Unit

}