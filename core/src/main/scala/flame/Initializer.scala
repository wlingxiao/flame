package flame

trait Initializer extends Handler {

  def receive(ctx: Context): Receive = {
    case HandlerAdded() => init(ctx.channel)
  }

  def init(ch: Channel): Unit

}
