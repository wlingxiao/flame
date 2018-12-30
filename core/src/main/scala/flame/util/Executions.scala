package flame
package util

import scala.concurrent.ExecutionContext

object Executions {

  implicit val directec: ExecutionContext = new ExecutionContext {

    def execute(runnable: Runnable): Unit = runnable.run()

    def reportFailure(t: Throwable): Unit = {
      throw t
    }
  }
}
