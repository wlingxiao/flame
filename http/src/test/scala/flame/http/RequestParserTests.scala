package flame
package http

import java.nio.ByteBuffer

import org.scalatest.{FunSuite, Matchers}

class RequestParserTests extends FunSuite with Matchers {

  test("parse request header") {
    val parser = new RequestParserImpl
    val in = ByteBuffer.wrap("GET / HTTP/1.1\r\nHost: localhost\r\nConnection: keep-alive\r\n\r\n".getBytes())

    parser.parsePrelude(in) shouldBe true
    val request = parser.getRequestPrelude
    request.method shouldEqual "GET"
    request.url shouldEqual "/"
    request.majorVersion shouldEqual 1
    request.minorVersion shouldEqual 1
    request.headers.toMap.get("Host") shouldEqual Some("localhost")
    request.headers.toMap.get("Connection") shouldEqual Some("keep-alive")
  }

}
