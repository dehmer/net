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

import java.io.{File, FileInputStream}
import java.nio.ByteBuffer
import java.nio.channels.{SelectableChannel, SocketChannel}
import scala.annotation.tailrec
import scala.collection.mutable

import io.bitbang.Combinators

/**
 * Helper used to queue and perform write operations.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
final class WriteOperations {

  import WriteOperations._

  // TODO: unbounded number of write ops per channel is probably not such a good idea.
  private val ops = mutable.Map[SelectableChannel, mutable.ArrayBuffer[WriteOperation]]()

  def append(channel: SocketChannel, op: WriteOperation): Unit = {
    ops.getOrElse(channel, mutable.ArrayBuffer[WriteOperation]()) K { xs =>
      xs.append(op)
      ops += (channel -> xs)
    }
  }

  def flush(channel: SocketChannel): Boolean = {

    @tailrec
    def flush(xs: mutable.ArrayBuffer[WriteOperation]): Boolean = {
      if (xs.isEmpty) true
      else xs.head(channel) match {
        case 0 => xs.remove(0); flush(xs)
        case _ => false
      }
    }

    flush(ops(channel))
  }

  def drop(channel: SelectableChannel): Unit = ops -= channel
  def isEmpty(channel: SelectableChannel): Boolean = ops.get(channel) match {
    case Some(buf) => buf.isEmpty
    case None      => true
  }
}

object WriteOperations {
  type WriteOperation = SocketChannel => Long

  /** Factory for buffer write operations. */
  def writeBufferOp(buffer: Array[Byte], off: Int, len: Int): WriteOperation = {
    val src = ByteBuffer.wrap(buffer, off, len)

    channel => {
      channel.write(src)
      src.remaining()
    }
  }

  /** Factory for file write operations. */
  def writeFileOp(file: File, off: Long, len: Long): WriteOperation = {
    val stream = new FileInputStream(file)
    val fileChannel = stream.getChannel
    var transferred = 0L

    channel => {
      transferred += fileChannel.transferTo(off + transferred, len - transferred, channel)
      len - transferred K { remaining =>
        // This closes the file channel as well:
        if (remaining == 0) stream.close()
      }
    }
  }
}