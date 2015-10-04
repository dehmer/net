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

package io.bitbang.http

import io.bitbang.net.{WriteBufferReq, WriteStringReq, ErrorInd}
import io.bitbang.pipeline.{Context, Layer, MessageHandler}

/**
 * @author Horst Dehmer
 */
class ResponseEncoder extends Layer {
  override def handleDownstream(context: Context) = {
    case header: MessageHeader => messageHeader(context, header)
    case Body(content)         => body(context, content)
    case unhandled             => context.sendDownstream(unhandled)
  }

  override def handleUpstream(context: Context): MessageHandler = {
    case ErrorInd(exception) => exception.printStackTrace()
    case unhandled           => context.sendUpstream(unhandled)
  }

  private def messageHeader(context: Context, messageHeader: MessageHeader): Unit = {
    val sb = new StringBuilder
    sb.append(messageHeader.startLine)
    sb.append("\r\n")

    messageHeader.headers.foreach { header =>
      sb.append(header)
      sb.append("\r\n")
    }

    sb.append("\r\n")

    context.sendDownstream(WriteStringReq(sb.toString()))
  }

  private def body(context: Context, content: Array[Byte]): Unit = {
    context.sendDownstream(WriteBufferReq(content))
  }
}
