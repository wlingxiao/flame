package flame

import scala.concurrent.{Future, Promise}

trait HandlerContext {

  def handler: Handler

  def channel: Channel

  def pipeline: Pipeline

  def sendReceived(msg: Object): HandlerContext

  /**
    * A [[Channel]] is active now, which means it is connected.
    */
  def sendConnected(): HandlerContext

  def write(msg: Object): Future[Int]

  def write(msg: Object, promise: Promise[Int]): Future[Int]

  def close(): Future[Int]

  def close(promise: Promise[Int]): Future[Int]
}