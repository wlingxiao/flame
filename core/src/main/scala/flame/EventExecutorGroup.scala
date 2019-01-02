package flame

import scala.concurrent.Future

trait EventExecutorGroup {

  def next: EventExecutor

  def apply[T](task: => T): Future[T]

}
