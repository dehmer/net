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

import scala.collection.mutable.ListBuffer

import io.bitbang.pipeline.Context

final class Response(requestHeader: MessageHeader, context: Context) {
  private var headers = Map[String, Array[String]]()

  def setHeader(name: String, values: Array[String]): Unit = this.headers += (name -> values)
  def setHeader(name: String, value: String): Unit = setHeader(name, Array(value))
  def getHeader(name: String): Array[String] = headers(name)
  def send(body: String): Unit = send(body.getBytes)

  def send(body: Array[Byte]): Unit = {
    val headers = ListBuffer[String]()

    if(transientConnection) headers += header(GeneralHeader.Connection, Token.Close)

    headers += header(EntityHeader.ContentLength, body.length)

    val messageHeader = MessageHeader("HTTP/1.1 200 OK", headers)
    context.sendDownstream(messageHeader)
    context.sendDownstream(Body(body))
  }

  private def transientConnection = requestHeader.hasValue(GeneralHeader.Connection, Token.Close)
}
