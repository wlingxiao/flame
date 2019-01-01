package flame
package http

import java.nio.ByteBuffer

import flame.util.BufferUtils

class RequestDecoder extends Handler {


  def receive(ctx: Context): Receive = {
    case read@Inbound.Read(msg) =>
      msg match {
        case buf: ByteBuffer =>
          decode(ctx, buf)
        case _ => ctx.send(read)
      }
  }

  private lazy val parser = new RequestParserImpl

  private[this] var buffered: ByteBuffer = BufferUtils.emptyBuffer

  private[this] var body: ByteBuffer = _


  def decode(ctx: Context, in: ByteBuffer): Unit = {
    buffered = BufferUtils.concatBuffers(buffered, in)
    if (parser.parsePrelude(buffered)) {
      if (body == null) {
        body = parseBody(buffered)
      } else {
        body = BufferUtils.concatBuffers(this.body, parseBody(buffered))
      }
      if (parser.contentComplete) {
        val request = parser.getRequestPrelude.copy(body = body)
      }
    }
  }


  private def readAndGetRequest(ctx: Context, msg: ByteBuffer): Request = {
    buffered = BufferUtils.concatBuffers(buffered, msg)
    maybeGetRequest(ctx, msg)
  }

  private def maybeGetRequest(ctx: Context, in: ByteBuffer): Request = {
    if (parser.parsePrelude(in)) {
      val body = parseBody(in)
      if (parser.contentComplete) {
        val request = parser.getRequestPrelude
        request.copy(body = body)
      } else null
    } else null
  }

  private def parseBody(in: ByteBuffer): ByteBuffer = {
    if (parser.contentComplete) BufferUtils.emptyBuffer
    else {
      parser.parseBody(in)
    }
  }

}
