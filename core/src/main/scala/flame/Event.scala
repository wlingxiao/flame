package flame

import java.net.SocketAddress

import scala.concurrent.Promise

sealed trait Event

trait Inbound extends Event

trait Outbound extends Event

case class HandlerAdded() extends Event

/**
  * Called once a bind operation is made.
  */
case class Bind(localAddress: SocketAddress, promise: Promise[Channel]) extends Outbound

/**
  * Intercepts [[Context.read()]]
  */
case class Read() extends Outbound

/**
  * Called once a write operation is made. The write operation will write the messages through the
  * [[Pipeline]]. Those are then ready to be flushed to the actual [[Channel]] once
  * [[Channel.flush]] is called
  */
case class Write(msg: Any, promise: Promise[Channel]) extends Outbound

case class Flush() extends Outbound

/**
  * The [[Channel]] of the [[Context]] is now active
  */
case class Active() extends Inbound

/**
  * The [[Channel]] of the [[Context]] was registered with its [[EventLoop]]
  */
case class Registered() extends Inbound

/**
  * Invoked when the current [[Channel]] has read a message from the peer.
  */
case class ChannelRead(msg: Any) extends Inbound