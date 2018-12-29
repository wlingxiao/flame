package flame
package util

import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

object BufferUtils {

  val emptyBuffer: ByteBuffer = allocate(0)

  def allocate(size: Int): ByteBuffer = ByteBuffer.allocate(size)

  def joinBuffers(buffers: Seq[ByteBuffer]): ByteBuffer = buffers match {
    case Seq() => emptyBuffer
    case Seq(b) => b
    case _ =>
      val sz = buffers.foldLeft(0)((sz, o) => sz + o.remaining())
      val b = allocate(sz)
      buffers.foreach(b.put)

      b.flip()
      b
  }

  def concatBuffers(oldbuff: ByteBuffer, newbuff: ByteBuffer): ByteBuffer =
    if (oldbuff == null) {
      if (newbuff == null) emptyBuffer
      else newbuff
    } else if (newbuff == null)
      oldbuff // already established that oldbuff is not `null`
    else if (!oldbuff.hasRemaining) newbuff
    else if (!newbuff.hasRemaining) oldbuff
    else if (!oldbuff.isReadOnly && oldbuff
      .capacity() >= oldbuff.limit() + newbuff.remaining()) {
      // Enough room to append newbuff to the end tof oldbuff
      oldbuff
        .mark()
        .position(oldbuff.limit())
        .limit(oldbuff.limit() + newbuff.remaining())

      oldbuff
        .put(newbuff)
        .reset()

      oldbuff
    } else { // Need to make a larger buffer
      val n = allocate(oldbuff.remaining() + newbuff.remaining())
      n.put(oldbuff)
        .put(newbuff)
        .flip()

      n
    }

  def takeSlice(buffer: ByteBuffer, size: Int): ByteBuffer = {

    if (size < 0 || size > buffer.remaining()) {
      throw new IllegalArgumentException(s"Invalid size: $size. buffer: $buffer")
    }

    val currentLimit = buffer.limit()
    buffer.limit(buffer.position() + size)
    val slice = buffer.slice()

    buffer
      .position(buffer.limit())
      .limit(currentLimit)
    slice
  }

  def bufferToString(buffer: ByteBuffer, charset: Charset = StandardCharsets.UTF_8): String =
    if (!buffer.hasRemaining) ""
    else {
      val arr = new Array[Byte](buffer.remaining())
      buffer.get(arr)
      new String(arr, charset)
    }

  def bufferToByteArry(buffer: ByteBuffer): Array[Byte] = {
    if (buffer.isDirect) {
      val arrLen = buffer.limit() - buffer.position()
      val arr = new Array[Byte](arrLen)
      buffer.get(arr)
      arr
    } else buffer.array()
  }
}
