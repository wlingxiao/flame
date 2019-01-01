package flame.http

import java.nio.ByteBuffer

import flame.util.BufferUtils

sealed trait HeaderState

object HeaderState {

  object Start extends HeaderState

  object HeaderInName extends HeaderState

  object HeaderInValue extends HeaderState

  object End extends HeaderState

}

sealed trait ChunkState

object ChunkState {

  object Start extends ChunkState

  object ChunkSize extends ChunkState

  object ChunkParams extends ChunkState

  object Chunk extends ChunkState

  object ChunkLf extends ChunkState

  object ChunkTrailers extends ChunkState

  object End extends ChunkState

}

sealed trait EndOfContent

object EndOfContent {

  object UnknownContent extends EndOfContent

  object NoContent extends EndOfContent

  object ContentLength extends EndOfContent

  object ChunkedContent extends EndOfContent

  object EofContent extends EndOfContent

  object End extends EndOfContent

}

trait BodyAndHeaderParser extends ParserBase {

  def maxChunkSize: Int

  def headerSizeLimit: Int

  private var headerState: HeaderState = HeaderState.Start
  private var contentLength: Long = 0
  private var contentPosition: Long = 0
  private var chunkLength = 0
  private var chunkState: ChunkState = _
  private var endOfContent: EndOfContent = _
  private var headerName: String = _


  protected val HTTP10Bytes: Array[Char] = "HTTP/1.0".toCharArray
  protected val HTTP11Bytes: Array[Char] = "HTTP/1.1".toCharArray

  protected val HTTPS10Bytes: Array[Char] = "HTTPS/1.0".toCharArray
  protected val HTTPS11Bytes: Array[Char] = "HTTPS/1.1".toCharArray

  // init
  internalReset()

  override def reset(): Unit = {
    super.reset()
    internalReset()
  }

  private def internalReset(): Unit = {
    headerState = HeaderState.Start
    chunkState = ChunkState.Start
    endOfContent = EndOfContent.UnknownContent

    contentLength = 0
    contentPosition = 0
    chunkLength = 0
  }

  override def shutdownParser(): Unit = {
    headerState = HeaderState.End
    chunkState = ChunkState.End
    endOfContent = EndOfContent.End
    super.shutdownParser()
  }

  protected def headerComplete(name: String, value: String): Boolean

  final def headersComplete: Boolean = {
    headerState == HeaderState.End
  }

  def mustNotHaveBody: Boolean

  def contentComplete: Boolean = {
    mustNotHaveBody ||
      endOfContent == EndOfContent.End ||
      endOfContent == EndOfContent.NoContent
  }

  final def isChunked: Boolean = {
    endOfContent == EndOfContent.ChunkedContent
  }

  final def isChunkedHeaders: Boolean = {
    chunkState == ChunkState.ChunkTrailers
  }

  final def definedContentLength: Boolean = {
    endOfContent == EndOfContent.ContentLength
  }

  final def getContentType: EndOfContent = {
    endOfContent
  }

  protected final def parseHeaders(in: ByteBuffer): Boolean = {
    while (true) {

      if (headerState == HeaderState.Start) {
        headerState = HeaderState.HeaderInName
        resetLimit(headerSizeLimit)
      }

      if (headerState == HeaderState.HeaderInName) {
        var ch = next(in, allow8859 = false)
        while (ch != ':' && ch != HttpToken.LF) {
          if (ch == HttpToken.EMPTY_BUFF) return false
          putChar(ch)
          ch = next(in, allow8859 = false)
        }

        if (bufferPosition == 0) {
          headerState = HeaderState.End
          if (chunkState == ChunkState.ChunkTrailers) {
            shutdownParser()
          } else if (endOfContent == EndOfContent.UnknownContent && mustNotHaveBody) {
            shutdownParser()
          }
          return true
        }

        if (ch == HttpToken.LF) {
          val name = getString
          clearBuffer()
          if (headerComplete(name, "")) return true
          parseHeaders(in)
          // continue headerLoop; Still parsing Header name
        }
        headerName = getString
        clearBuffer()
        headerState = HeaderState.HeaderInValue
      }

      if (headerState == HeaderState.HeaderInValue) {
        var ch = next(in, allow8859 = true)
        while (ch != HttpToken.LF) {
          if (ch == HttpToken.EMPTY_BUFF) return false
          putChar(ch)
          ch = next(in, allow8859 = true)
        }

        val value = getTrimmedString
        clearBuffer()
        if (chunkState != ChunkState.ChunkTrailers) {
          if (headerName.equalsIgnoreCase("Transfer-Encoding")) {
            if (value.equalsIgnoreCase("chunked")) {
              if (endOfContent != EndOfContent.End) {
                endOfContent = EndOfContent.ChunkedContent
              } else if (value.equalsIgnoreCase("identity")) {

              } else {
                shutdownParser()
                // TODO: server should return 501 - https://tools.ietf.org/html/rfc7230#page-30
                throw new IllegalArgumentException("Unknown Transfer-Encoding: " + value)
              }
            }
          } else if (endOfContent != EndOfContent.ChunkedContent && headerName.equalsIgnoreCase("Content-Length")) {
            val len = value.toLong
            if ((endOfContent == EndOfContent.NoContent || endOfContent == EndOfContent.ContentLength) && len != contentLength) {
              val oldLen = contentLength
              shutdownParser()
              throw new IllegalArgumentException("Duplicate Content-Length headers detected: " +
                oldLen + " and " + len + "\n")
            } else if (len > 0) {
              contentLength = len
              endOfContent = EndOfContent.ContentLength
            } else if (len == 0) {
              contentLength = len
              endOfContent = EndOfContent.NoContent
            } else {
              shutdownParser()
              throw new IllegalArgumentException("Cannot have negative Content-Length: '" + len + "'\n")
            }
          }
        }
        if (headerComplete(headerName, value)) {
          return true
        }
        headerState = HeaderState.HeaderInName
        return parseHeaders(in)
      }

      if (headerState == HeaderState.End) {
        shutdownParser()
        throw new IllegalArgumentException("Header parser reached invalid position.")
      }
    }
    false
  }

  protected final def parseContent(in: ByteBuffer): ByteBuffer = {
    if (!contentComplete) {
      if (endOfContent == EndOfContent.UnknownContent) {
        endOfContent = EndOfContent.EofContent
        contentLength = Long.MaxValue
        return parseContent(in)
      }

      if (endOfContent == EndOfContent.ContentLength) {

      }
      if (endOfContent == EndOfContent.ContentLength || endOfContent == EndOfContent.EofContent) {
        return nonChunkedContent(in)
      }
      if (endOfContent == EndOfContent.ChunkedContent) {
        return chunkedContent(in)
      }
      throw new IllegalArgumentException("not implemented: " + endOfContent)
    } else throw new IllegalArgumentException("content already complete: " + endOfContent)
  }

  private def nonChunkedContent(in: ByteBuffer): ByteBuffer = {
    val remaining = contentLength - contentPosition
    val bufSize = in.remaining
    if (bufSize >= remaining) {
      contentPosition += remaining
      val result = submitPartialBuffer(in, remaining.toInt)
      shutdownParser()
      result
    } else {
      if (bufSize > 0) {
        contentPosition += bufSize
        submitBuffer(in)
      } else null
    }
  }

  private def chunkedContent(in: ByteBuffer): ByteBuffer = {
    while (true) {
      // sw:
      if (chunkState == ChunkState.Start) {
        chunkState = ChunkState.ChunkSize
        resetLimit(256)
      }
      if (chunkState == ChunkState.ChunkSize) {
        assert(contentPosition == 0)
        while (true) {
          val ch = next(in, allow8859 = false)
          if (ch == HttpToken.EMPTY_BUFF) return null
          if (HttpToken.isWhiteSpace(ch) || ch == HttpToken.SEMI_COLON) {
            chunkState = ChunkState.ChunkParams
            return chunkedContent(in)
            //break;  // Break out of the while loop, and fall through to params
          } else if (ch == HttpToken.LF) {
            if (chunkLength == 0) {
              headerState = HeaderState.Start
              chunkState = ChunkState.ChunkTrailers
            } else {
              chunkState = ChunkState.Chunk
            }
            return chunkedContent(in)
            // break sw;
          } else {
            chunkLength = 16 * chunkLength + HttpToken.hexCharToInt(ch)
            if (chunkLength > maxChunkSize) {
              shutdownParser()
              throw new IllegalArgumentException("Chunk length too large: " + chunkLength)
            }
          }
        }
      }

      if (chunkState == ChunkState.ChunkParams) {
        var ch = next(in, allow8859 = false)
        while (ch != HttpToken.LF) {
          if (ch == HttpToken.EMPTY_BUFF) return null
          ch = next(in, allow8859 = false)
        }

        if (chunkLength == 0) {
          headerState = HeaderState.Start
          chunkState = ChunkState.ChunkTrailers
        } else {
          chunkState = ChunkState.Chunk
        }
        // break
      } else if (chunkState == ChunkState.Chunk) {
        val remainingChunkSize = chunkLength - contentPosition
        val buffSize = in.remaining()
        if (remainingChunkSize <= buffSize) {
          val result = submitPartialBuffer(in, remainingChunkSize.toInt)
          contentPosition = 0
          chunkLength = 0
          chunkState = ChunkState.ChunkLf
          return result
        } else {
          if (buffSize > 0) {
            contentPosition += buffSize
            return submitBuffer(in)
          } else return null
        }
      }
      if (chunkState == ChunkState.ChunkLf) {
        val ch = next(in, allow8859 = false)
        if (ch == HttpToken.EMPTY_BUFF) return null
        if (ch != HttpToken.LF) {
          shutdownParser()
          throw new IllegalArgumentException("Bad chunked encoding char: '" + ch + "'")
        }
        chunkState = ChunkState.Start
        // break;
      } else if (chunkState == ChunkState.ChunkTrailers) {
        if (parseHeaders(in)) {
          return BufferUtils.emptyBuffer
        } else return null
      }
    }
    null
  }

  private def submitBuffer(in: ByteBuffer) = {
    val out = in.asReadOnlyBuffer
    in.position(in.limit)
    out
  }

  private def submitPartialBuffer(in: ByteBuffer, size: Int) = { // Perhaps we are just right? Might be common.
    if (size == in.remaining) submitBuffer(in)
    else BufferUtils.takeSlice(in, size).asReadOnlyBuffer
  }

}
