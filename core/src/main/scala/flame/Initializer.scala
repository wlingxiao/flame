package flame

trait Initializer extends Handler {

  def apply(ctx: Context, ev: Event): Unit = {
    ev match {
      case HandlerAdded() =>
        init(ctx.channel)
      case _ =>
        ctx.send(ev)
    }
  }

  def init(ch: Channel): Unit

}
