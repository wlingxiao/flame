package flame

import java.util.concurrent.{Executor, ThreadFactory}

class ThreadPerTaskExecutor(threadFactory: ThreadFactory) extends Executor {
  override def execute(task: Runnable): Unit = {
    threadFactory.newThread(task).start()
  }
}
