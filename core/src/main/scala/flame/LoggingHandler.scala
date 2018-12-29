package flame

import flame.logging.Logging

import scala.concurrent.Promise

class LoggingHandler extends Handler with Logging {

  override def connected(ctx: HandlerContext): Unit = {
    log.info(s"Connected: ${ctx.channel}")
    ctx.sendConnected()
  }

  override def received(ctx: HandlerContext, msg: Object): Unit = {
    log.info(s"Received: ${ctx.channel}")
    ctx.sendReceived(msg)
  }

  override def close(ctx: HandlerContext, promise: Promise[Int]): Unit = {
    ctx.close(promise)
  }
}
