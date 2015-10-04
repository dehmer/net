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

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

/**
 * Fixed-length read buffer used by selector loop to read from channels.
 *
 * @param capacity Initial, fixed buffer capacity.
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
final class ReadBuffer(capacity: Int) {
  private val buffer = ByteBuffer.allocate(capacity)

  def read(channel: SocketChannel): Option[Array[Byte]] = {
    buffer.clear()
    channel.read(buffer) match {
      case -1        => None
      case bytesRead =>
        val data = new Array[Byte](bytesRead)
        // NOTE: This method does not work with direct buffers.
        System.arraycopy(buffer.array(), 0, data, 0, bytesRead)
        Some(data)
    }
  }
}