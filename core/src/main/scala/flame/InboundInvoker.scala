package flame

trait InboundInvoker {

  def send(in: Inbound): Unit

}
