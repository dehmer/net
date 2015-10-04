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

import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

/**
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
object EchoExample extends App {

  val latch = new CountDownLatch(1)

  lazy val client: SelectorLoop = SelectorLoop("TCP-CLIENT") {
    case TcpConnected(channel) => client.write(channel, "hello".reverse.getBytes)
    case TcpData(channel, buffer) if "olleh" == new String(buffer) => client.close(channel)
    case TcpClosed(channel) => latch.countDown()
    case _ => /* ignore TcpDrain. */
  }

  lazy val server: SelectorLoop = SelectorLoop("TCP-SERVER") {
    case TcpBound(channel) => client.connect(channel.getLocalAddress)
    case TcpData(channel, buffer) => server.write(channel, buffer)
    case TcpEnd(_) => /* client closed its side of the connection. */
    case TcpClosed(_) => /* server auto-closed its connection after TcpEnd. */
    case _ => /* ignore TcpAccepted and TcpDrain. */
  }

  server.bind(new InetSocketAddress(0))
  latch.await()
  client.close()
  server.close()
}
