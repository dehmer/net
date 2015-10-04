/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Syncpoint GmbH (http://www.syncpoint.io/)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.bitbang.net

import java.io.File
import java.nio.charset.{Charset, StandardCharsets}

/**
 * Requests handled by [[SocketLayer]].
 * [[SocketRequest]]s are translated to selector loop API calls.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
sealed trait SocketRequest

object WriteBufferReq {
  def apply(buffer: Array[Byte]): WriteBufferReq = WriteBufferReq(buffer, 0, buffer.length)
}

final case class BufferSizeReq(size: Int) extends SocketRequest
final case class WriteBufferReq(buffer: Array[Byte], offset: Int, length: Int) extends SocketRequest
final case class WriteStringReq(s: String, charset: Charset = StandardCharsets.UTF_8) extends SocketRequest
final case class WriteFileReq(file: File, offset: Int, length: Int) extends SocketRequest
case object CloseReq extends SocketRequest
case object SuspendReq extends SocketRequest
case object ResumeReq extends SocketRequest
final case class MessageReq(message: AnyRef) extends SocketRequest