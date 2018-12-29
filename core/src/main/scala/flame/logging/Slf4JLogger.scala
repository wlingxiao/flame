package flame.logging

private[flame] class Slf4JLogger(underlying: org.slf4j.Logger) extends Logger {

  override def trace(msg: => String): Unit = {
    if (underlying.isTraceEnabled) {
      underlying.trace(msg)
    }
  }

  override def trace(msg: => String, t: Throwable): Unit = {
    if (underlying.isTraceEnabled) {
      underlying.trace(msg, t)
    }
  }

  override def debug(msg: => String): Unit = {
    if (underlying.isDebugEnabled) {
      underlying.debug(msg)
    }
  }

  override def debug(msg: => String, t: Throwable): Unit = {
    if (underlying.isDebugEnabled) {
      underlying.debug(msg, t)
    }
  }

  override def info(msg: => String): Unit = {
    if (underlying.isInfoEnabled) {
      underlying.info(msg)
    }
  }

  override def info(msg: => String, t: Throwable): Unit = {
    if (underlying.isInfoEnabled()) {
      underlying.info(msg, t)
    }
  }

  override def warn(msg: => String): Unit = {
    if (underlying.isWarnEnabled) {
      underlying.warn(msg)
    }
  }

  override def warn(msg: => String, t: Throwable): Unit = {
    if (underlying.isWarnEnabled) {
      underlying.warn(msg, t)
    }
  }

  override def error(msg: String): Unit = {
    if (underlying.isErrorEnabled) {
      underlying.error(msg)
    }
  }

  override def error(msg: => String, t: => Throwable): Unit = {
    if (underlying.isErrorEnabled) {
      underlying.error(msg, t)
    }
  }

}
