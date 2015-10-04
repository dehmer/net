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

import java.nio.channels.{SelectableChannel, ServerSocketChannel => SSC, SocketChannel => SC}

/**
 * Events dispatched from [[SelectorLoop]].
 *
 * <ul>
 *   <li>[[TcpError]]: An (I/O) error occurred for a channel.
 *   NOTE: [[TcpError]] is always followed by [[TcpClosed]].
 *   </li>
 *   <li>[[TcpClosed]]: A channel has been closed.</li>
 *   <li>[[TcpBound]]: A server channel has been bound to a local address.</li>
 *   <li>[[TcpAccepted]]: A channel has been accepted by a server channel.</li>
 *   <li>[[TcpEnd]]: A peer closed its side of a connection.
 *   NOTE: [[TcpEnd]] is always followed by [[TcpClosed]].
 *   </li>
 *   <li>[[TcpData]]: Data has been read from a channel.</li>
 *   <li>[[TcpConnected]]: A client channel has been connected to a server.</li>
 *   <li>[[TcpDrain]]: All previously pending write operations are now completed.</li>
 *   <li>[[TcpOverflow]]: A write operation could not complete, because of a buffer overflow.
 *   NOTE: The operation is still pending and continues to write on the next select cycle.
 *   </li>
 *   <li>[[TcpSuspended]]: A channel has been suspended,
 *   i.e. the channel is currently not interested in either OP_READ nor OP_WRITE.
 *   </li>
 *   <li>[[TcpResumed]]: A suspended channel has been resumed.</li>
 *   <li>[[TcpReceived]]: A user defined message has been dispatched (through selector thread)
 *   for a given channel.
 *   </li>
 * </ul>
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
sealed trait TcpEvent

final case class TcpError(channel: SelectableChannel, exception: Throwable) extends TcpEvent
final case class TcpClosed(channel: SelectableChannel) extends TcpEvent
final case class TcpBound(channel: SSC) extends TcpEvent
final case class TcpAccepted(serverChannel: SSC, channel: SC) extends TcpEvent
final case class TcpEnd(channel: SC) extends TcpEvent
final case class TcpData(channel: SC, buffer: Array[Byte]) extends TcpEvent
final case class TcpConnected(channel: SC) extends TcpEvent
final case class TcpDrain(channel: SC) extends TcpEvent
final case class TcpOverflow(channel: SC) extends TcpEvent
final case class TcpSuspended(channel: SC) extends TcpEvent
final case class TcpResumed(channel: SC) extends TcpEvent
final case class TcpReceived(channel: SC, message: AnyRef) extends TcpEvent
