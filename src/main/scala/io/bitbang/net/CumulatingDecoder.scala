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

import io.bitbang.pipeline.{Context, UpstreamLayer}
import java.nio.{BufferUnderflowException, ByteBuffer}
import scala.annotation.tailrec

/**
 * Provides a buffer for parsing upstream backend messages
 * into higher-level messages passed on to upper application layer.
 * NOTE: This layer is stateful.
 * It accumulates incomplete frames, i.e. byte buffers from TCP,
 * until they can be decoded.
 */
abstract class CumulatingDecoder extends UpstreamLayer with Decoder {

  /* Holds un-parsed data from previous decode cycle(s), if any. */
  private var acc = new Array[Byte](0)

  override def handleUpstream(context: Context) = {
    case DataInd(buffer) => handle(context, updateBuffer(buffer))
    case other           => context.sendUpstream(other)
  }

  def decode(context: Context, buffer: ByteBuffer): Unit

  private def handle(context: Context, buffer: ByteBuffer): Unit = {
    try doDecode(context, buffer)
    catch {
      case underflow: BufferUnderflowException =>
        // remember un-decoded data for next decode cycle:
        buffer.reset()
        acc = get(buffer, buffer.remaining())
    }
  }

  @tailrec private def doDecode(context: Context, buffer: ByteBuffer): Unit = {
    if (buffer.hasRemaining) {
      buffer.mark()
      decode(context, buffer)
      doDecode(context, buffer)
    } else acc = new Array[Byte](0)
  }

  private def updateBuffer(bytes: Array[Byte]): ByteBuffer = {
    // append new buffer to un-parsed data, if necessary:
    if (acc.length == 0) acc = bytes
    else {
      val c = new Array[Byte](acc.length + bytes.length)
      System.arraycopy(acc, 0, c, 0, acc.length)
      System.arraycopy(bytes, 0, c, acc.length, bytes.length)
      acc = c
    }

    ByteBuffer.wrap(acc)
  }
}
