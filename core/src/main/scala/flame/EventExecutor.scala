package flame

trait EventExecutor extends EventExecutorGroup {

  def inEventLoop: Boolean

  def parent: EventExecutorGroup

}
