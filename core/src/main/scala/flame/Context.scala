package flame

trait Context extends InboundInvoker with OutboundInvoker {

  def channel: Channel = {
    pipeline.channel
  }

  def handler: Handler

  def pipeline: Pipeline

  def name: String

  def executor: EventExecutor

}