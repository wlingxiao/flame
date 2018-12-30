package flame

import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean

import flame.logging.Logging

import scala.concurrent.{Future, Promise}

trait Pipeline {

  def channel: Channel

  def append(handler: Handler): Pipeline

  def bind(localAddress: SocketAddress): Unit

  def read(): Pipeline

  def write(msg: Any): Future[Channel]

  def flush(): Pipeline

  def sendChannelRegistered(): Pipeline

  def sendChannelActive(): Unit

  def sendChannelRead(msg: Any): Pipeline

  def sendChannelReadComplete(): Unit

}

object Pipeline {

  def apply(ch: Channel): Pipeline = new Pipeline.Impl(ch)

  class Impl(val channel: Channel) extends Pipeline {

    private val head: HeadContext = HeadContext(this)

    private val tail: TailContext = TailContext(this)

    private var firstRegistration = true

    private var registered = false

    private var pendingHandlerCallbackHead: PendingHandlerCallback = _

    head.next = tail
    tail.prev = head

    override def append(handler: Handler): Pipeline = {
      val newCtx = newContext(handler)
      val prev = tail.prev
      newCtx.prev = prev
      newCtx.next = tail
      prev.next = newCtx
      tail.prev = newCtx
      if (!registered) {
        newCtx.setAddPending()
        callHandlerCallbackLater(newCtx, added = true)
      }
      this
    }

    override def bind(localAddress: SocketAddress): Unit = {
      tail.bind(localAddress)
    }

    override def write(msg: Any): Future[Channel] = {
      tail.write(msg)
    }

    override def flush(): Pipeline = {
      tail.flush()
      this
    }

    override def sendChannelActive(): Unit = {
      head.apply(head, Active())
    }

    override def sendChannelRegistered(): Pipeline = {
      head.apply(head, Registered())
      this
    }

    override def sendChannelRead(msg: Any): Pipeline = {
      head.apply(head, ChannelRead(msg))
      this
    }

    override def sendChannelReadComplete(): Unit = {

    }

    private def newContext(handler: Handler): AbstractContext = {
      ContextImpl(handler.getClass.getSimpleName, this, handler)
    }

    override def read(): Pipeline = {
      tail.read()
      this
    }

    def invokeHandlerAddedIfNeeded(): Unit = {
      assert(channel.eventLoop.inEventLoop)
      if (firstRegistration) {
        firstRegistration = false
        registered = true
        var task = pendingHandlerCallbackHead
        while (task != null) {
          task.execute()
          task = task.next
        }
      }
    }

    def callHandlerCallbackLater(ctx: AbstractContext, added: Boolean): Unit = {
      val task = new PendingHandlerAddedTask(ctx, this)
      var pending = pendingHandlerCallbackHead
      if (pending == null) {
        pendingHandlerCallbackHead = task
      } else {
        while (pending.next != null) {
          pending = pending.next
        }
        pending.next = task
      }

    }

    def callHandlerAdded0(ctx: AbstractContext): Unit = {
      ctx.setAddComplete()
      ctx.handler.apply(ctx, HandlerAdded())
    }

  }

  object HandlerState {
    val Init = 0
    val AddPending = 1
    val AddComplete = 2
    val RemoveComplete = 3
  }

  abstract class PendingHandlerCallback(ctx: Context) extends Runnable {
    var next: PendingHandlerCallback = _

    def execute(): Unit
  }

  class PendingHandlerAddedTask(ctx: AbstractContext, pipeline: Pipeline.Impl) extends PendingHandlerCallback(ctx) {
    override def execute(): Unit = {
      val executor = ctx.executor
      if (executor.inEventLoop) {
        pipeline.callHandlerAdded0(ctx)
      } else {
        executor.execute(this)
      }
    }

    override def run(): Unit = {
      pipeline.callHandlerAdded0(ctx)
    }
  }

  abstract class AbstractContext extends Context {

    @volatile
    private var handlerState = HandlerState.Init

    var prev: AbstractContext = _

    var next: AbstractContext = _

    def outbound: Boolean

    def inbound: Boolean

    def setAddPending(): Unit = {
      handlerState = HandlerState.AddPending
    }

    def setAddComplete(): Unit = {
      handlerState = HandlerState.AddComplete
    }

    def bind(socketAddress: SocketAddress): Future[Channel] = {
      bind(socketAddress, Promise[Channel]())
    }

    def bind(localAddress: SocketAddress, promise: Promise[Channel]): Future[Channel] = {
      val next = findOutbound()
      next.handler.apply(next, Bind(localAddress, promise))
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
        case _: Inbound =>
          val next = findInbound()
          next.handler.apply(next, ev)
        case _: Outbound =>
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

    override def toString: String = {
      name
    }

    override def write(msg: Any): Future[Channel] = {
      write(msg, Promise[Channel]())
    }

    override def write(msg: Any, promise: Promise[Channel]): Future[Channel] = {
      val next = findOutbound()
      val executor = next.executor
      if (executor.inEventLoop) {
        next.handler.apply(next, Write(msg, promise))
      } else {
        ???
      }
      promise.future
    }

    override def flush(): Context = ???

    override def executor: EventExecutor = pipeline.channel.eventLoop
  }

  case class HeadContext(pipeline: Pipeline.Impl, name: String = "HEAD") extends AbstractContext with Handler {

    private val unsafe = pipeline.channel.unsafe

    override def outbound: Boolean = {
      true
    }

    override def inbound: Boolean = false

    override def handler: Handler = this

    def apply(ctx: Context, ev: Event): Unit = {
      ev match {
        case Bind(localAddress, promise) =>
          unsafe.bind(localAddress, promise)
        case Read() =>
          unsafe.beginRead()
        case Registered() =>
          pipeline.invokeHandlerAddedIfNeeded()
          ctx.send(ev)
        case Active() =>
          ctx.send(ev)
          channel.read()
        case Write(msg, promise) =>
          unsafe.write(msg, promise)
        case Flush() =>
          unsafe.flush()
        case _ => ctx.send(ev)
      }
    }

    override def flush(): Context = super.flush()
  }

  case class TailContext(pipeline: Pipeline, name: String = "TAIL") extends AbstractContext with Handler with Logging {
    override def handler: Handler = {
      this
    }

    override def apply(ctx: Context, ev: Event): Unit = {
      ev match {
        case ChannelRead(msg) =>
          log.warn(s"Discarded inbound message $msg that reached at the tail of the pipeline. ")
        case _: Inbound =>
        case _ => ctx.send(ev)
      }
    }

    override def outbound: Boolean = false

    override def inbound: Boolean = true
  }

  case class ContextImpl(name: String,
                         pipeline: Pipeline.Impl,
                         handler: Handler) extends AbstractContext {
    override def outbound: Boolean = false

    override def inbound: Boolean = true
  }

}