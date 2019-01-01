package flame.http

import java.nio.ByteBuffer

sealed trait LineState

object LineState {

  object Start extends LineState

  object Method extends LineState

  object Space1 extends LineState

  object Path extends LineState

  object Space2 extends LineState

  object Version extends LineState

  object End extends LineState

}

abstract class RequestParser extends BodyAndHeaderParser {

  private var lineState: LineState = LineState.Start

  def maxRequestLineSize: Int

  private var method: String = _
  private var path: String = _

  def submitRequestLine(method: String, path: String, scheme: String, majorVersion: Int, minorVersion: Int): Boolean

  def requestLineComplete: Boolean = {
    lineState eq LineState.End
  }

  def isStart: Boolean = {
    lineState eq LineState.Start
  }

  override def reset(): Unit = {
    super.reset()
    internalReset()
  }

  // init
  internalReset()

  private def internalReset(): Unit = {
    lineState = LineState.Start
  }

  override def shutdownParser(): Unit = {
    super.shutdownParser()
    lineState = LineState.End
  }

  def mustNotHaveBody: Boolean = {
    !(definedContentLength || isChunked)
  }

  def parseRequestLine(in: ByteBuffer): Boolean = {
    while (true) {
      if (lineState eq LineState.Start) {
        lineState = LineState.Method
        resetLimit(maxRequestLineSize)
      }

      if (lineState eq LineState.Method) {
        var ch = next(in, allow8859 = false)
        while (HttpToken.A <= ch && ch <= HttpToken.Z) {
          putChar(ch)
          ch = next(in, allow8859 = false)
        }
        if (ch == HttpToken.EMPTY_BUFF) return false
        method = getString
        clearBuffer()

        if (!HttpToken.isWhiteSpace(ch)) {
          val badMethod = method + ch
          shutdownParser()
          throw new IllegalArgumentException("Invalid request method: '" + badMethod + "'")
        }
        lineState = LineState.Space1
      }

      if (lineState eq LineState.Space1) {
        var ch = next(in, allow8859 = false)
        while (ch == HttpToken.SPACE || ch == HttpToken.TAB) {
          ch = next(in, allow8859 = false)
        }
        if (ch == HttpToken.EMPTY_BUFF) return false
        putChar(ch)
        lineState = LineState.Path
      }

      if (lineState eq LineState.Path) {
        var ch = next(in, allow8859 = false)
        while (ch != HttpToken.SPACE && ch != HttpToken.TAB) {
          if (ch == HttpToken.EMPTY_BUFF) return false
          putChar(ch)
          ch = next(in, allow8859 = false)
        }
        path = getString
        clearBuffer()
        lineState = LineState.Space2
      }

      if (lineState eq LineState.Space2) {
        var ch = next(in, allow8859 = false)
        while (ch == HttpToken.SPACE || ch == HttpToken.TAB) {
          ch = next(in, allow8859 = false)
        }
        if (ch == HttpToken.EMPTY_BUFF) return false
        if (ch != 'H') {
          shutdownParser()
          throw new IllegalArgumentException("Http version started with illegal character: " + ch)
        }
        putChar(ch)
        lineState = LineState.Version
      }

      if (lineState eq LineState.Version) {
        var ch = next(in, allow8859 = false)
        while (ch != HttpToken.LF) {
          if (ch == HttpToken.EMPTY_BUFF) return false
          putChar(ch)
          ch = next(in, allow8859 = false)
        }

        var majorVersion = -1
        var minorVersion = -1
        var scheme = ""
        if (arrayMatches(HTTP11Bytes)) {
          majorVersion = 1
          minorVersion = 1
          scheme = "http"
        } else if (arrayMatches(HTTPS11Bytes)) {
          majorVersion = 1
          minorVersion = 1
          scheme = "https"
        } else if (arrayMatches(HTTP10Bytes)) {
          majorVersion = 1
          minorVersion = 0
          scheme = "http"
        } else if (arrayMatches(HTTPS10Bytes)) {
          majorVersion = 1
          minorVersion = 0
          scheme = "https"
        } else {
          val reason = "Bad HTTP version: " + getString
          clearBuffer()
          shutdownParser()
          throw new IllegalArgumentException(reason)
        }

        clearBuffer()
        lineState = LineState.End
        return !submitRequestLine(method, path, scheme, majorVersion, minorVersion)
      }
      throw new IllegalArgumentException("Attempted to parse Request line when already " +
        "complete. LineState: '" + lineState + "'")
    }
    false
  }

}
