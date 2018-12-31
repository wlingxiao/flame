package flame

import java.net.SocketAddress

import flame.logging.Logging

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}

trait Pipeline {

  def channel: Channel

  def append(handler: Handler): Pipeline

  def bind(localAddress: SocketAddress): Unit

  def read(): Pipeline

  def write(msg: Any): Future[Channel]

  def deregister(): Future[Channel]

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

    override def deregister(): Future[Channel] = {
      tail.deregister()
    }

    override def sendChannelActive(): Unit = {
      head.receive(head)(Inbound.Active())
    }

    override def sendChannelRegistered(): Pipeline = {
      head.receive(head)(Inbound.Registered())
      this
    }

    override def sendChannelRead(msg: Any): Pipeline = {
      head.receive(head)(Inbound.Read(msg))
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
      val added = HandlerAdded()
      val receive = ctx.handler.receive(ctx)
      if (receive.isDefinedAt(added)) {
        receive(added)
      }
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
      invokeOutbound(_ => Outbound.Bind(localAddress, promise))
      promise.future
    }

    private def invokeOutbound(fn: AbstractContext => Outbound): Unit = {
      @tailrec
      def go(ctx: AbstractContext, fn: AbstractContext => Outbound): Unit = {
        val receive = ctx.handler.receive(ctx)
        val ev = fn(ctx)
        if (receive.isDefinedAt(ev)) {
          receive(ev)
        } else go(ctx.prev, fn)
      }

      go(prev, fn)
    }

    private def invokeInbound(fn: AbstractContext => Inbound): Unit = {
      @tailrec
      def go(ctx: AbstractContext, fn: AbstractContext => Inbound): Unit = {
        val receive = ctx.handler.receive(ctx)
        val ev = fn(ctx)
        if (receive.isDefinedAt(ev)) {
          receive(ev)
        } else go(ctx.next, fn)
      }

      go(next, fn)
    }

    def deregister(): Future[Channel] = {
      ???
      /*val next = findOutbound()
      val executor = next.executor
      val promise = Promise[Channel]()
      if (executor.inEventLoop) {
        next.handler.apply(next, Outbound.Deregister(promise))
      }
      promise.future*/
    }

    override def send(ev: Event): Unit = {
      ev match {
        case in: Inbound =>
          invokeInbound(_ => in)
        case out: Outbound =>
          invokeOutbound(_ => out)
        case _ => throw new IllegalStateException(ev.getClass.getName)
      }
    }

    override def read(): Context = {
      invokeOutbound(_ => Outbound.Read())
      this
    }

    override def toString: String = {
      name
    }

    override def write(msg: Any): Future[Channel] = {
      write(msg, Promise[Channel]())
    }

    override def write(msg: Any, promise: Promise[Channel]): Future[Channel] = {
      invokeOutbound(_ => Outbound.Write(msg, promise))
      promise.future
    }

    override def flush(): Context = {
      invokeOutbound(_ => Outbound.Flush())
      this
    }

    override def executor: EventExecutor = pipeline.channel.eventLoop
  }

  case class HeadContext(pipeline: Pipeline.Impl, name: String = "HEAD") extends AbstractContext with Handler {

    private val unsafe = pipeline.channel.unsafe

    override def handler: Handler = this

    def receive(ctx: Context): Receive = {
      case Outbound.Bind(localAddress, promise) =>
        unsafe.bind(localAddress, promise)
      case Outbound.Read() =>
        unsafe.beginRead()
      case Outbound.Write(msg, promise) =>
        unsafe.write(msg, promise)
      case Outbound.Flush() =>
        unsafe.flush()
      case Outbound.Deregister(promise) =>
        unsafe.deregister(promise)
      case ev@Inbound.Registered() =>
        pipeline.invokeHandlerAddedIfNeeded()
        ctx.send(ev)
      case ev@Inbound.Active() =>
        ctx.send(ev)
        channel.read()
      case ev: Event =>
        ctx.send(ev)
    }

    override def flush(): Context = super.flush()
  }

  case class TailContext(pipeline: Pipeline, name: String = "TAIL") extends AbstractContext with Handler with Logging {
    override def handler: Handler = {
      this
    }

    def receive(ctx: Context): Receive = {
      case Inbound.Read(msg) =>
        log.warn(s"Discarded inbound message $msg that reached at the tail of the pipeline. ")
      case _: Inbound =>
    }
  }

  case class ContextImpl(name: String,
                         pipeline: Pipeline.Impl,
                         handler: Handler) extends AbstractContext {
  }

}