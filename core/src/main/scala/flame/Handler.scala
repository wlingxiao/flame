package flame

import java.net.SocketAddress

import scala.concurrent.Promise

trait Handler extends ((Context, Event) => Unit) {

  def apply(ctx: Context, ev: Event): Unit
}
