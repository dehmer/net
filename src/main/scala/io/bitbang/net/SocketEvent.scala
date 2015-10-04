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

/**
 * Events sent from [[Bootstrap]] to the bottom layer of a [[io.bitbang.pipeline.Pipeline]],
 * which is usually a [[SocketLayer]].
 * [[SocketEvent]]s are very similar to, and created from, [[TcpEvent]]s.
 *
 */
sealed trait SocketEvent

final case class SoOpen(socket: Socket) extends SocketEvent
final case class SoError(exception: Throwable) extends SocketEvent
final case class SoData(buffer: Array[Byte]) extends SocketEvent
case object SoClose extends SocketEvent
case object SoEnd extends SocketEvent
case object SoDrain extends SocketEvent
case object SoOverflow extends SocketEvent
case object SoSuspended extends SocketEvent
case object SoResumed extends SocketEvent
final case class SoReceived(message: AnyRef) extends SocketEvent