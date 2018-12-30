package flame

import java.net.SocketAddress

import scala.concurrent.Promise

sealed trait Event

trait In extends Event

trait Out extends Event

case class HandlerAdded() extends Event

object In {

  /**
    * The [[Channel]] of the [[Context]] is now active
    */
  case class Active() extends In

  case class Inactive() extends In

  /**
    * The [[Channel]] of the [[Context]] was registered with its [[EventLoop]]
    */
  case class Registered() extends In

  case class Unregistered() extends In

  /**
    * Invoked when the current [[Channel]] has read a message from the peer.
    */
  case class Read(msg: Any) extends In

  case class ReadComplete() extends In

}

object Out {

  /**
    * Called once a bind operation is made.
    */
  case class Bind(localAddress: SocketAddress, promise: Promise[Channel]) extends Out

  case class Connect(remoteAddress: SocketAddress, localAddress: SocketAddress, promise: Promise[Channel]) extends Out

  case class Disconnect(promise: Promise[Channel]) extends Out

  case class Close(promise: Promise[Channel]) extends Out

  case class Deregister(promise: Promise[Channel]) extends Out

  /**
    * Intercepts [[Context.read()]]
    */
  case class Read() extends Out

  /**
    * Called once a write operation is made. The write operation will write the messages through the
    * [[Pipeline]]. Those are then ready to be flushed to the actual [[Channel]] once
    * [[Channel.flush]] is called
    */
  case class Write(msg: Any, promise: Promise[Channel]) extends Out

  case class Flush() extends Out

}