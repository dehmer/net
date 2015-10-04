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

import io.bitbang.pipeline.{Context, MessageHandler, UpstreamLayer}
import io.bitbang.Combinators

/**
 * @author Horst Dehmer
 */
class RequestHandler extends UpstreamLayer {
  type Behavior = (Context) => MessageHandler
  private var behavior   = messageHeader
  private var bodyBuffer = new Array[Byte](0)

  override def handleUpstream(context: Context) = behavior(context)

  private def messageHeader: Behavior = (context) => {
    case header: MessageHeader if complete(header) => context.sendUpstream(new Request(header))
    case header: MessageHeader                     => behavior = messageBody(header)
    case unhandled                                 => context.unhandledMessage(unhandled)
  }

  private def messageBody(header: MessageHeader): Behavior = (context) => {
    case Body(content)  => body(context, header, content)
    case Chunk(content) => chunk(context, header, content)
    case unhandled      => context.unhandledMessage(unhandled)
  }

  private def complete(header: MessageHeader): Boolean = {
    if (header.hasField(EntityHeader.ContentLength)) false
    else if (header.hasValue(GeneralHeader.TransferEncoding, Token.Chunked)) false
    else true
  }

  private def body(context: Context, header: MessageHeader, content: Array[Byte]): Unit = {
    context.sendUpstream(new Request(header, content))
    behavior = messageHeader
  }

  private def chunk(context: Context, header: MessageHeader, content: Array[Byte]): Unit = {
    bodyBuffer = new Array[Byte](bodyBuffer.length + content.length) K { buffer =>
      System.arraycopy(bodyBuffer, 0, buffer, 0, bodyBuffer.length)
      System.arraycopy(content, 0, buffer, bodyBuffer.length, content.length)
    }

    if (content.length == 0) {
      context.sendUpstream(new Request(header, bodyBuffer))
      bodyBuffer = new Array[Byte](0)
      behavior = messageHeader
    }
  }
}
