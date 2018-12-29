package flame

import java.nio.ByteBuffer

import flame.logging.Logging

import scala.concurrent.{Future, Promise}


trait Pipeline {

  var channel: Channel

  def addLast(handler: Handler): Pipeline

  def sendConnected(): Unit

  def sendReceived(msg: Object): Unit
}

private object Pipeline {

  class Impl extends Pipeline {

    var channel: Channel = _

    private val head: AbstractHandlerContext = new HeadContext(this)

    private val tail: AbstractHandlerContext = new TailContext(this)

    head.next = tail
    tail.prev = head

    def addLast(handler: Handler): Pipeline = {
      val prev = tail.prev
      val newCtx = new AbstractHandlerContextImpl(this, handler)
      newCtx.prev = prev
      newCtx.next = tail
      prev.next = newCtx
      tail.prev = newCtx
      this
    }

    def sendConnected(): Unit = {
      head.sendConnected()
    }

    def sendReceived(msg: Object): Unit = {
      head.handler.received(head, msg)
    }

  }

  def apply(): Pipeline = {
    new Impl
  }

  class HeadContext(pipeline: Pipeline) extends AbstractHandlerContext(pipeline, null) with Handler {

    override def handler: Handler = this

    override def received(ctx: HandlerContext, msg: Object): Unit = {
      ctx.sendReceived(msg)
    }

    override def write(ctx: HandlerContext, msg: Object, promise: Promise[Int]): Unit = {
      msg match {
        case buf: ByteBuffer =>
          val channel = ctx.channel.asInstanceOf[NIO1Channel]
          channel.loop.write(channel, buf, promise)
        case _ => throw new UnsupportedOperationException
      }
    }

    override def close(ctx: HandlerContext, promise: Promise[Int]): Unit = {
      pipeline.channel.close()
    }
  }

  class TailContext(pipeline: Pipeline) extends AbstractHandlerContext(pipeline, null) with Handler with Logging {
    override def received(ctx: HandlerContext, msg: Object): Unit = {
      log.info(s"Discarded message $msg that reached at the tail of the  pipeline.")
    }

    override def handler: Handler = this

    override def close(ctx: HandlerContext, promise: Promise[Int]): Unit = {
      ctx.close(promise)
    }
  }

}
