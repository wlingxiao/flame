package flame
package http

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class ResponseEncoder extends Handler {

  private val CRLFBytes = "\r\n".getBytes(StandardCharsets.US_ASCII)

  private val terminationBytes =
    "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

  def receive(ctx: Context): Receive = {
    case Outbound.Write(msg, promise) =>
      msg match {
        case res: Response =>
          encode(ctx, res)
        case _ => ctx.write(msg, promise)
      }
  }

  private def encode(ctx: Context, response: Response): Unit = {
    val sb = new StringBuilder(512)
    sb.append(response.version)
      .append(' ')
      .append(response.code)
      .append(' ')
      .append(response.reason)
      .append("\r\n")

    for ((name, value) <- response.headers) {
      sb.append(name)
        .append(": ")
        .append(value)
        .append("\r\n")
    }

    sb.append("\r\n")
    ctx.write(ByteBuffer.wrap(sb.toString().getBytes()))

    if (isChunked(response)) {
      ctx.write(encodeChunkedContent(response.body, response.body.remaining()))
      ctx.write(encodeChunkedContent(null, 0))
      ctx.flush()
    } else {
      ctx.write(response.body)
      ctx.write(ByteBuffer.wrap("\r\n".getBytes()))
      ctx.flush()
    }
  }

  private def isChunked(res: Response): Boolean = {
    res.headers.exists { header =>
      "Transfer-Encoding".equalsIgnoreCase(header._1) && "chunked".equalsIgnoreCase(header._2)
    }
  }

  private def encodeChunkedContent(body: ByteBuffer, contentLength: Int): ByteBuffer = {
    if (contentLength > 0) {
      val chunkSize = Integer.toHexString(contentLength).getBytes(StandardCharsets.US_ASCII)
      val b = ByteBuffer.allocate(2 + chunkSize.length + 2 + body.remaining() + 2)
      b.put(chunkSize).put(CRLFBytes).put(body).put(CRLFBytes).flip()
      b
    } else {
      ByteBuffer.wrap(terminationBytes)
    }
  }

}
