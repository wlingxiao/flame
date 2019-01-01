package flame.http

import java.nio.ByteBuffer
import java.util

import scala.annotation.tailrec

trait ParserBase {
  protected def initialBufferSize: Int

  protected def isLenient: Boolean

  protected var bufferPosition = 0

  private var internalBuffer = new Array[Char](initialBufferSize)

  private var segmentByteLimit = 0
  private var segmentBytePosition = 0

  def reset(): Unit = {
    clearBuffer()
  }

  def shutdownParser(): Unit = {
    clearBuffer()
  }

  protected final def clearBuffer(): Unit = {
    bufferPosition = 0
  }

  protected final def putChar(c: Char): Unit = {
    val len = internalBuffer.length
    if (len == bufferPosition) { // expand
      val next = new Array[Char](2 * len + 1)
      System.arraycopy(internalBuffer, 0, next, 0, bufferPosition)
      internalBuffer = next
    }
    internalBuffer(bufferPosition) = c
    bufferPosition += 1
  }

  protected final def getString: String = {
    getString(0, bufferPosition)
  }

  protected final def getString(end: Int): String = {
    getString(0, end)
  }

  protected final def getString(start: Int, end: Int): String = {
    if (end > bufferPosition) {
      throw new IndexOutOfBoundsException("Requested: " + end + ", max: " + bufferPosition)
    }
    new String(internalBuffer, start, end)
  }

  protected final def getTrimmedString: String = {
    if (bufferPosition != 0) {
      @tailrec
      def loop1(i: Int): (Int, Boolean) = {
        if (i < bufferPosition) {
          val ch = internalBuffer(i)
          if (ch == '"') {
            i -> true
          } else if (ch != HttpToken.SPACE && ch != HttpToken.TAB) {
            i -> false
          } else loop1(i + 1)
        } else i -> false
      }

      val (start, quoted) = loop1(0)

      @tailrec
      def loop2(end: Int): Int = {
        if (end > start) {
          val ch = internalBuffer(end - 1)
          if (quoted) {
            if (ch == '"') end
            else if (ch != HttpToken.SPACE && ch != HttpToken.TAB) {
              throw new IllegalArgumentException("String might not quoted correctly: '" + getString + "'")
            } else loop2(end - 1)
          } else if (ch != HttpToken.SPACE && ch != HttpToken.TAB) end
          else loop2(end - 1)
        } else end
      }

      val end = loop2(bufferPosition)
      new String(internalBuffer, start, end - start)
    } else ""
  }

  protected final def arrayMatches(chars: Array[Char]): Boolean = {
    if (chars.length == bufferPosition) {
      for (i <- 0 until bufferPosition) {
        if (chars(i) != internalBuffer(i)) {
          return false
        }
      }
      true
    } else false
  }

  protected final def resetLimit(limit: Int): Unit = {
    segmentByteLimit = limit
    segmentBytePosition = 0
  }

  private var cr = false

  protected final def next(buffer: ByteBuffer, allow8859: Boolean): Char = {
    if (buffer.hasRemaining) {
      if (segmentByteLimit <= segmentBytePosition) {
        shutdownParser()
        throw new IllegalArgumentException("Request length limit exceeded: " + segmentByteLimit)
      }

      val b = buffer.get()
      segmentBytePosition += 1
      if (cr) {
        if (b != HttpToken.LF) {
          throw new IllegalArgumentException("Invalid sequence: LF didn't follow CR: " + b)
        }
        cr = false
        return b.asInstanceOf[Char]
      }
      if (b < HttpToken.SPACE) {
        if (b == HttpToken.CR) {
          cr = true
          return next(buffer, allow8859)
        } else if (b == HttpToken.TAB || allow8859 && b < 0) {
          return (b & 0xff).asInstanceOf[Char]
        } else if (isLenient) {
          return HttpToken.REPLACEMENT.asInstanceOf[Char]
        } else {
          shutdownParser()
          throw new IllegalArgumentException("Invalid char: '" + (b & 0xff).asInstanceOf[Char] + "', 0x" + Integer.toHexString(b));
        }

      }
      b.asInstanceOf[Char]
    } else HttpToken.EMPTY_BUFF.asInstanceOf[Char]
  }

}





























