package flame.http

object HttpToken {

  val EMPTY_BUFF = 0xFFFF

  val REPLACEMENT = 0xFFFD

  val COLON = ':'
  val TAB = '\t'
  val LF = '\n'
  val CR = '\r'
  val SPACE = ' '
  val CRLF = Array(CR, LF)
  val SEMI_COLON = ';'

  val ZERO = '0'
  val NINE = '9'
  val A = 'A'
  val F = 'F'
  val Z = 'Z'
  val a = 'a'
  val f = 'f'
  val z = 'z'

  def hexCharToInt(ch: Char): Int = if (ZERO <= ch && ch <= NINE) ch - ZERO
  else if (a <= ch && ch <= f) ch - a + 10
  else if (A <= ch && ch <= F) ch - A + 10
  else throw new IllegalArgumentException("Bad hex char: " + ch.toChar)

  @inline def isDigit(ch: Char): Boolean = NINE >= ch && ch >= ZERO

  @inline def isHexChar(ch: Byte): Boolean = ZERO <= ch && ch <= NINE || a <= ch && ch <= f || A <= ch && ch <= F

  @inline def isWhiteSpace(ch: Char): Boolean = ch == SPACE || ch == TAB

}
