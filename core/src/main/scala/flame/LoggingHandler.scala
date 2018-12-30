package flame

import flame.logging.Logging

class LoggingHandler extends Handler with Logging {
  override def apply(ctx: Context, ev: Event): Unit = {
    ev match {
      case In.Registered() =>
        log.info(s"Registered: $ctx")
      case In.Unregistered() =>
        log.info(s"Unregistered: $ctx")
      case In.Active() =>
        log.info(s"Active: $ctx")
      case In.Inactive() =>
        log.info(s"Inactive: $ctx")
      case In.Read(msg) =>
        log.info(s"Read: $ctx $msg")
      case Out.Bind(localAddress, _) =>
        log.info(s"Bind: $localAddress")
      case Out.Read() =>
        log.info(s"Read: $ctx")
      case Out.Write(msg, _) =>
        log.info(s"Write: $ctx $msg")
      case Out.Close(_) =>
        log.info(s"Close: $ctx")
      case _ =>
    }
    ctx.send(ev)
  }
}
