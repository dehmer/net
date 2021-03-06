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

/**
 * Generic message header for requests and responses.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
case class MessageHeader(startLine: String, headers: ListBuffer[String]) {
  def hasField(name: String) = headers.exists(s => s.startsWith(name + ":"))

  def hasValue(name: String, value: String) = headers.contains(name + ": " + value.toLowerCase)
  def intValue(name: String): Int = headers.find(s => s.startsWith(name + ":")) match {
    case Some(field) => field.substring((name + ": ").length).toInt
    case None        => throw new IllegalArgumentException("header-field not found: " + name)
  }
}

case class Body(content: Array[Byte])
case class Chunk(content: Array[Byte])

