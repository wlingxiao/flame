package flame.http

import java.nio.ByteBuffer

case class Request(method: String,
                   url: String,
                   majorVersion: Int,
                   minorVersion: Int,
                   headers: Seq[(String, String)],
                   body: ByteBuffer)

