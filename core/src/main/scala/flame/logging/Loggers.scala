package flame.logging

private[flame] object Loggers {

  def getLogger(clazz: Class[_]): Logger = {
    new Slf4JLogger(org.slf4j.LoggerFactory.getLogger(clazz))
  }

}
