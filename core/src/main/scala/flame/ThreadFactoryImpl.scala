package flame

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class ThreadFactoryImpl(poolName: String) extends ThreadFactory {

  private val poolId = new AtomicInteger()

  private val prefix = poolName + "-" + poolId.incrementAndGet() + '-'

  private val threadId = new AtomicInteger()

  override def newThread(task: Runnable): Thread = {
    new Thread(task, prefix + threadId.getAndIncrement())
  }
}
