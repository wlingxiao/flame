package flame

import java.net.SocketAddress

sealed trait Event

trait InboundEvent extends Event

trait OutboundEvent extends Event

case class HandlerAdded() extends Event

case class Bind(localAddress: SocketAddress) extends OutboundEvent

case class Read() extends OutboundEvent

case class Active() extends InboundEvent