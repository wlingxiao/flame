package flame

trait Handler {
  type Receive = PartialFunction[Event, Unit]

  def receive(ctx: Context): Receive
}
