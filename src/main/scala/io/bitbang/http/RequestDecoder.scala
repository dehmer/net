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

import java.nio.ByteBuffer
import scala.collection.mutable.ListBuffer
import io.bitbang.net.{CumulatingDecoder, Decoder}
import io.bitbang.pipeline.Context

// $ curl http://localhost:8080
// $ curl -XPOST -H"Content-Type: application/json" -d '{"foo": "bar"}' http://localhost:8080
// $ curl -XPOST --header "Transfer-Encoding: chunked" --data-binary @dataset.json http://localhost:8080

/**
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
class RequestDecoder extends CumulatingDecoder {

  //  Request = Request-Line              ; Section 5.1
  //            *(( general-header        ; Section 4.5
  //             | request-header         ; Section 5.3
  //             | entity-header ) CRLF)  ; Section 7.1
  //            CRLF
  //            [ message-body ]          ; Section 4.3

  // Request-Line = Method SP Request-URI SP HTTP-Version CRLF

  private var decoder: Decoder = requestHeader

  override def decode(context: Context, buffer: ByteBuffer) = {
    decoder.decode(context, buffer)
  }

  // Informally we define Request-Header as
  //
  //  Request-Header = Request-Line
  //                   *(( general-header
  //                    | request-header
  //                    | entity-header ) CRLF)
  //                   CRLF

  private def requestHeader: Decoder = new Decoder {
    override def decode(context: Context, buffer: ByteBuffer): Unit = {
      val requestLine = readLine(buffer)

      val headers = ListBuffer[String]()
      var header = readLine(buffer)
      while ("" != header) {
        headers += header
        header = readLine(buffer)
      }

      val messageHeader = MessageHeader(requestLine, headers)
      context.sendUpstream(messageHeader)

      // The presence of a message-body in a request is signaled by the inclusion
      // of a Content-Length or Transfer-Encoding header field in
      // the request’s message-headers.
      decoder = body(messageHeader)
    }
  }

  /**
   * Determines body decoder after request line and headers are read.
   * Note that if no body is expected, decoder is reset to 'Request-Line' decoder.
   */
  private def body(header: MessageHeader): Decoder = {
    if (header.hasField(EntityHeader.ContentLength)) fixedBody(header)
    else if (header.hasValue(GeneralHeader.TransferEncoding, Token.Chunked)) chunkedBody
    else requestHeader
  }

  def fixedBody(header: MessageHeader): Decoder = {
    val contentLength = header.intValue(EntityHeader.ContentLength)
    var remaining = contentLength
    val acc = new Array[Byte](contentLength)

    new Decoder {
      override def decode(context: Context, buffer: ByteBuffer): Unit = {
        val length = Math.min(remaining, buffer.remaining)
        buffer.get(acc, contentLength - remaining, length)
        remaining -= length

        if (remaining == 0) {
          context.sendUpstream(Body(acc))
          decoder = requestHeader
        }
      }
    }
  }

  //  Chunked-Body = *chunk
  //                 last-chunk
  //                 trailer
  //                 CRLF
  //  chunk        = chunk-size [ chunk-extension ] CRLF
  //                 chunk-data CRLF
  //  chunk-size   = 1*HEX
  //  last-chunk   = 1*("0") [ chunk-extension ] CRLF
  //
  //  chunk-extension = *( ";" chunk-ext-name [ "=" chunk-ext-val ] )
  //  chunk-ext-name  = token
  //  chunk-ext-val   = token | quoted-string
  //  chunk-data      = chunk-size(OCTET)
  //  trailer         = *(entity-header CRLF)

  def chunkedBody: Decoder = new Decoder {
    var chunkSize: Option[Int] = None
    var remaining              = 0
    var acc      : Array[Byte] = new Array[Byte](0)

    override def decode(context: Context, buffer: ByteBuffer): Unit = {
      chunkSize match {
        case None       => readChunkSize(context, buffer)
        case Some(size) => readChunk(context, buffer, size)
      }
    }

    private def readChunkSize(context: Context, buffer: ByteBuffer): Unit = {
      remaining = Integer.parseInt(readLine(buffer), 16)
      chunkSize = Some(remaining)
      acc = new Array[Byte](remaining)

      if (remaining == 0) {
        context.sendUpstream(Chunk(acc))
        decoder = requestHeader
        // NOTE: possible trailer is ignored (3.6.1)
      }
    }

    private def readChunk(context: Context, buffer: ByteBuffer, size: Int): Unit = {
      val length = Math.min(remaining, buffer.remaining)
      buffer.get(acc, size - remaining, length)
      remaining -= length

      if (remaining == 0) {
        // chunk-data is terminated with CR/LF: read empty line.
        readLine(buffer)
        context.sendUpstream(Chunk(acc))
        chunkSize = None
      }
    }
  }

  private def readLine(buffer: ByteBuffer) = readString(buffer, "\r\n")

  /**
   * Tries to read a string from `buffer` until all `delimiter` characters are found.
   * `delimiter` is excluded from the resulting string.
   */
  private def readString(buffer: ByteBuffer, delimiter: String): String = {
    val sb = new StringBuilder
    var ix = 0

    var ch = buffer.get.toChar
    while (ix < delimiter.length) {
      if (delimiter(ix) == ch) ix += 1
      else sb.append(ch)
      if (ix < delimiter.length) ch = buffer.get.toChar
    }

    sb.toString()
  }
}