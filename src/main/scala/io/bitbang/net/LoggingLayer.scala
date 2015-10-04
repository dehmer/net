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

import org.slf4j.LoggerFactory

import io.bitbang.pipeline.{Context, Layer}

final class LoggingLayer extends Layer {
  private val logger     = LoggerFactory.getLogger(classOf[LoggingLayer])
  private val byteToChar = (for (i <- 0 to 255) yield if (i >= 32 && i < 127) i.toChar else '.').toArray

  override def handleUpstream(context: Context) = {
    case message@DataInd(buffer) => dump(buffer); context.sendUpstream(message)
    case message                 => context.sendUpstream(message)
  }

  override def handleDownstream(context: Context) = {
    case message@WriteBufferReq(buffer, offset, length) => dump(slice(buffer, offset, length)); context.sendDownstream(message)
    case message                                        => context.sendDownstream(message)
  }

  private def slice(buffer: Array[Byte], offset: Int, length: Int): Array[Byte] = {
    println("buffer: " + buffer + ", offset: " + offset + ", length: " + length)
    val slice = new Array[Byte](length)
    System.arraycopy(buffer, offset, slice, 0, length)
    slice
  }

  private def dump(buffer: Array[Byte]): Unit = {
    val builder = new StringBuilder()
    builder.append("buffer-length: " + buffer.length + " (0x" + Integer.toHexString(buffer.length) + ")\n")
    builder.append("         +-------------------------------------------------+\n")
    builder.append("         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |\n")
    builder.append("+--------+-------------------------------------------------+----------------+\n")

    // 16 bytes per row.
    val rowCount = buffer.length / 16
    val lastBytes = buffer.length - 16 * rowCount

    var offset = 0
    for (i <- 0 until rowCount) {
      var s: String = ""
      builder.append("|" + intToHex(offset) + "|")
      for (c <- 0 until 16) {
        builder.append(" " + byteToHex(buffer(offset)))
        s += buffer(offset).asInstanceOf[Char]
        offset += 1
      }

      builder.append(" |" + s.map(c => byteToChar(c & 0xff)) + "|\n")
    }

    if (lastBytes > 0) {
      var s: String = ""
      builder.append("|" + intToHex(offset) + "|")
      for (c <- 0 until lastBytes) {
        builder.append(" " + byteToHex(buffer(offset)))
        s += buffer(offset).asInstanceOf[Char]
        offset += 1
      }

      for (x <- 1 to (16 - lastBytes)) builder.append("   ")
      s = s + Array.fill(16 - s.length)(' ').mkString
      builder.append(" |" + s.map(c => byteToChar(c & 0xff)) + "|\n")
    }

    builder.append("+--------+-------------------------------------------------+----------------+")
    logger.debug(builder.toString())
  }

  @inline private def byteToHex(b: Byte) = {
    val s = Integer.toHexString(b & 0xff)
    if (s.length != 2) "0" + s else s
  }

  @inline private def intToHex(i: Int) = {
    val s = Integer.toHexString(i)
    val padding = Array.fill(8 - s.length)('0').mkString
    padding + s
  }
}
