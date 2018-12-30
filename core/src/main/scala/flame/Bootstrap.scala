package flame

import java.net.InetSocketAddress

import scala.concurrent.Future

class Bootstrap {

  private var group: EventLoopGroup = _

  private var childGroup: EventLoopGroup = _

  private var channelFactory: () => Channel = _

  private var _channelHandler: Handler = _

  def channel(ch: => Channel): Bootstrap = {
    channelFactory = () => ch
    this
  }

  def group(parentGroup: EventLoopGroup, childGroup: EventLoopGroup): Bootstrap = {
    group = parentGroup
    this.childGroup = childGroup
    this
  }

  def bind(inetHost: String, inetPort: Int): Unit = {
    val channel = channelFactory()
    initChannel(channel)
    group.register(channel)
    channel.eventLoop.execute { () =>
      channel.bind(new InetSocketAddress(inetHost, inetPort))
    }
  }

  private def initChannel(ch: Channel): Unit = {
    val pipeline = ch.pipeline
    pipeline.append(new Initializer {
      def init(ch: Channel): Unit = {
        val pipeline = ch.pipeline
        ch.eventLoop.execute { () =>
          pipeline.append(new ServerBootstrapAcceptor(ch, childGroup, _channelHandler))
        }
      }
    })
  }

  def childHandler(handler: Handler): Unit = {
    _channelHandler = handler
  }

}

class ServerBootstrapAcceptor(ch: Channel, childGroup: EventLoopGroup, childHandler: Handler) extends Handler {
  def apply(ctx: Context, ev: Event): Unit = {
    ev match {
      case ChannelRead(msg) =>
        val channel = msg.asInstanceOf[Channel]
        channel.pipeline.append(childHandler)
        childGroup.register(channel)
      case _ => ctx.send(ev)
    }
  }
}
