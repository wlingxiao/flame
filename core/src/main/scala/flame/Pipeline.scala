package flame

import java.net.SocketAddress

import scala.concurrent.{Future, Promise}

trait Pipeline {

  def channel: Channel

  def append(handler: Handler): Pipeline

  def bind(localAddress: SocketAddress): Unit

  def read(): Pipeline

  def fireChannelActive(): Unit

}

object Pipeline {

  def apply(ch: Channel): Pipeline = new Pipeline.Impl(ch)

  class Impl(val channel: Channel) extends Pipeline {

    private val head: HeadContext = HeadContext(this)

    private val tail: TailContext = TailContext(this)

    head.next = tail
    tail.prev = head

    override def append(handler: Handler): Pipeline = {
      val newCtx = newContext(handler)
      val prev = tail.prev
      newCtx.prev = prev
      newCtx.next = tail
      prev.next = newCtx
      tail.prev = newCtx
      this
    }

    override def bind(localAddress: SocketAddress): Unit = {
      tail.bind(localAddress)
    }

    override def fireChannelActive(): Unit = {
      head.apply(head, Active())
    }

    private def newContext(handler: Handler): AbstractContext = {
      ContextImpl(this, handler)
    }

    override def read(): Pipeline = {
      tail.read()
      this
    }
  }

  abstract class AbstractContext extends Context {
    var prev: AbstractContext = _

    var next: AbstractContext = _

    def outbound: Boolean

    def inbound: Boolean

    def bind(socketAddress: SocketAddress): Future[Channel] = {
      bind(socketAddress, Promise[Channel]())
    }

    def bind(localAddress: SocketAddress, promise: Promise[Channel]): Future[Channel] = {
      val next = findOutbound()
      next.handler.apply(next, Bind(localAddress))
      promise.future
    }

    private def findOutbound(): AbstractContext = {
      var ctx = this
      do {
        ctx = ctx.prev
      } while (!ctx.outbound)
      ctx
    }

    private def findInbound(): AbstractContext = {
      var ctx = this
      do {
        ctx = ctx.next
      } while (!ctx.inbound)
      ctx
    }

    override def send(ev: Event): Unit = {
      ev match {
        case _: InboundEvent =>
          val next = findInbound()
          next.handler.apply(next, ev)
        case _: OutboundEvent =>
          val next = findOutbound()
          next.handler.apply(next, ev)
        case _ => throw new IllegalStateException(ev.getClass.getName)
      }
    }

    override def read(): Context = {
      val next = findOutbound()
      next.handler.apply(next, Read())
      this
    }
  }

  case class HeadContext(pipeline: Pipeline) extends AbstractContext with Handler {

    override def outbound: Boolean = {
      true
    }

    override def inbound: Boolean = false

    override def handler: Handler = this

    override def bind(localAddress: SocketAddress): Future[Channel] = {
      pipeline.channel.unsafe.bind(localAddress)
      null
    }

    def apply(ctx: Context, ev: Event): Unit = {
      ev match {
        case Bind(localAddress) =>
          pipeline.channel.unsafe.bind(localAddress)
        case Read() =>
          pipeline.channel.unsafe.beginRead()
        case Active() =>
          ctx.send(ev)
          channel.read()
        case _ => ctx.send(ev)
      }
    }
  }

  case class TailContext(pipeline: Pipeline) extends AbstractContext with Handler {
    override def handler: Handler = {
      this
    }

    override def apply(ctx: Context, ev: Event): Unit = {
      ev match {
        case _: InboundEvent =>
        case _ => ctx.send(ev)
      }
    }

    override def outbound: Boolean = false

    override def inbound: Boolean = true
  }

  case class ContextImpl(pipeline: Pipeline.Impl, handler: Handler) extends AbstractContext {
    override def outbound: Boolean = false

    override def inbound: Boolean = true
  }

}