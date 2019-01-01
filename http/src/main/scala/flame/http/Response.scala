package flame
package http

import java.nio.ByteBuffer

import flame.util.BufferUtils

case class Response(version: String,
                    code: Int,
                    reason: String,
                    headers: Iterable[(String, String)] = Nil,
                    body: ByteBuffer = BufferUtils.emptyBuffer)
