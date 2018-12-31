package flame

import java.net.InetSocketAddress

import scala.concurrent.Future
import scala.util.Success

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
    import flame.util.Executions._
    val channel = channelFactory()
    initChannel(channel)
    group.register(channel).onComplete {
      case Success(ch) =>
        ch.eventLoop.execute { () =>
          channel.bind(new InetSocketAddress(inetHost, inetPort))
        }
      case _ =>
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

  def receive(ctx: Context): Receive = {
    case Inbound.Read(msg) =>
      val channel = msg.asInstanceOf[Channel]
      channel.pipeline.append(childHandler)
      childGroup.register(channel)
  }
}
