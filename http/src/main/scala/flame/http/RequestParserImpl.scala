package flame
package http

import java.nio.ByteBuffer

import flame.util.BufferUtils

import scala.collection.mutable.ArrayBuffer

class RequestParserImpl extends RequestParser {

  private val headers = ArrayBuffer[(String, String)]()
  private var method: String = _
  private var uri: String = _
  private var major: Int = _
  private var minor: Int = _

  override def maxRequestLineSize: Int = 2048

  override def maxChunkSize: Int = Int.MaxValue

  override def headerSizeLimit: Int = 40 * 1024

  override def initialBufferSize: Int = 10 * 1024

  override def isLenient: Boolean = false

  override def submitRequestLine(method: String, path: String, scheme: String, majorVersion: Int, minorVersion: Int): Boolean = {
    this.uri = path
    this.method = method
    this.major = majorVersion
    this.minor = minorVersion
    false
  }

  override def headerComplete(name: String, value: String): Boolean = {
    headers += name -> value
    false
  }


  def parsePrelude(buffer: ByteBuffer): Boolean = {
    if (!requestLineComplete && !parseRequestLine(buffer)) false
    else if (!headersComplete && !parseHeaders(buffer)) false
    else true
  }

  def getRequestPrelude: Request = {
    Request(method, uri, major, minor, headers, null)
  }

  def parseBody(buffer: ByteBuffer): ByteBuffer = parseContent(buffer) match {
    case null => BufferUtils.emptyBuffer
    case buff => buff
  }

}
