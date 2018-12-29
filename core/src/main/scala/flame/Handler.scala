package flame

import scala.concurrent.Promise


trait Handler {

  def connected(ctx: HandlerContext): Unit = {
    ctx.sendConnected()
  }

  def received(ctx: HandlerContext, msg: Object): Unit = {
    ctx.sendReceived(msg)
  }

  def write(ctx: HandlerContext, msg: Object, promise: Promise[Int]): Unit = {
    ctx.write(msg, promise)
  }

  def close(ctx: HandlerContext, promise: Promise[Int]): Unit = {
    ctx.close(promise)
  }

}

