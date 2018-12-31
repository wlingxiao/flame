package flame

import flame.logging.Logging

class LoggingHandler extends Handler with Logging {
  def receive(ctx: Context): Receive = {
    case ev: Event =>
      log.info(ev.toString)
      ctx.send(ev)
  }
}
