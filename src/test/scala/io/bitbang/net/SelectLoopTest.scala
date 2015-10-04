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

import java.net.{ConnectException, InetSocketAddress, SocketAddress}
import java.nio.channels.{ServerSocketChannel => SSC, SocketChannel}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.ExecutionContext.Implicits.global
import org.junit.{Assert, Test}
import org.slf4j.LoggerFactory

class SelectLoopTest {
  private val logger         = LoggerFactory.getLogger(classOf[SelectLoopTest])
  private val message        = "welcome"
  private val defaultTimeout = 500L

  // low-level, event tests

  @Test def server$bind(): Unit = {
    val latch = new CountDownLatch(1)
    val loop = SelectorLoop("server$bind") {
      case TcpBound(channel) => latch.countDown()
      case _                 => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def server$close(): Unit = {
    val latch = new CountDownLatch(2)
    val loop = SelectorLoop("server$close") {
      case TcpClosed(_: SSC) => latch.countDown()
      case TcpBound(channel) => latch.countDown()
    }

    loop.bind(null)
    loop.close()
    latch.await(1L, TimeUnit.SECONDS)
    Assert.assertEquals("unexpected latch counter.", 0, latch.getCount)
  }

  @Test def server$accepted(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("server$accepted") {
      case TcpBound(ssc)     => loop.connect(ssc.getLocalAddress)
      case TcpAccepted(_, _) => latch.countDown()
      case _                 => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def client$connected(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("client$connected") {
      case TcpBound(serverChannel) => loop.connect(serverChannel.getLocalAddress)
      case TcpConnected(channel)   => latch.countDown()
      case _                       => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def server$drain(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("server$drain") {
      case TcpBound(ssc)      => loop.connect(ssc.getLocalAddress)
      case TcpAccepted(_, sc) => loop.write(sc, message)
      case TcpDrain(_)        => latch.countDown()
      case _                  => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def server$data(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("server$data") {
      case TcpBound(ssc)          => loop.connect(ssc.getLocalAddress)
      case TcpConnected(sc)       => loop.write(sc, message)
      case TcpData(_, data)       => if (message == new String(data)) latch.countDown()
      case TcpError(_, exception) => exception.printStackTrace()
      case _                      => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def server$end(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("server$end") {
      case TcpBound(ssc)    => loop.connect(ssc.getLocalAddress)
      case TcpConnected(sc) => loop.close(sc)
      case TcpEnd(_)        => latch.countDown()
      case _                => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def client$drain(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("client$drain") {
      case TcpBound(serverChannel) => loop.connect(serverChannel.getLocalAddress)
      case TcpConnected(channel)   => loop.write(channel, message)
      case TcpDrain(_)             => latch.countDown()
      case _                       => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def client$data(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("client$data") {
      case TcpBound(serverChannel) => loop.connect(serverChannel.getLocalAddress)
      case TcpAccepted(_, channel) => loop.write(channel, message)
      case TcpData(_, data)        => if (new String(data) == message) latch.countDown()
      case TcpError(_, exception)  => exception.printStackTrace()
      case _                       => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def client$end(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("client$end") {
      case TcpBound(serverChannel) => loop.connect(serverChannel.getLocalAddress)
      case TcpAccepted(_, channel) => loop.close(channel)
      case TcpEnd(_)               => latch.countDown()
      case _                       => /* ignore */
    }

    bindAndWait(loop, latch)
  }

  // bind/connect API tests

  @Test def bind$success(): Unit = {
    val latch = new CountDownLatch(1)
    val loop = new SelectorLoop("bind$success", loggingDispatch)

    loop.bind(null).onSuccess {
      case _: SSC => latch.countDown()
    }

    await(latch)
    loop.close()
  }

  @Test def bind$failure(): Unit = {
    val latch = new CountDownLatch(1)
    val loop = SelectorLoop("bind$failure") {
      case TcpError(_, exception) => latch.countDown()
      case _                      => /* ignore */
    }

    loop.bind(new InetSocketAddress("xyz", 52000))
    await(latch)
    loop.close()
  }

  @Test def connect$success(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("connect$success") {
      case TcpBound(ssc)   => loop.connect(ssc.getLocalAddress)
      case TcpConnected(_) => latch.countDown()
      case _               => /* ignore */
    }

    loop.bind(null)
    await(latch)
    loop.close()
  }

  @Test def connect$unconnected(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("connect$unconnected") {
      case TcpBound(_)    => loop.connect(null)
      case TcpError(_, _) => latch.countDown()
      case _              => /* ignore */
    }

    loop.connect(null)
    await(latch)
    loop.close()
  }

  @Test def connect$refused(): Unit = {
    val latch = new CountDownLatch(1)
    lazy val loop: SelectorLoop = SelectorLoop("connect$refused") {
      case TcpBound(_)            => loop.connect(new InetSocketAddress("localhost", 54321))
      case TcpError(_, exception) => if (exception.isInstanceOf[ConnectException]) latch.countDown()
      case _                      => /* ignore */
    }

    loop.bind(null)
    await(latch)
    loop.close()
  }

  // => test cases from the 'old' selector loop:

  @Test def accept$connect(): Unit = {
    val connectionCount = 10
    val latch = new CountDownLatch(connectionCount)
    def connectClients(endpoint: SocketAddress) = for (i <- 1 to connectionCount) loop.connect(endpoint)

    lazy val loop: SelectorLoop = SelectorLoop("accept$connect") {
      case TcpBound(serverChannel) => connectClients(serverChannel.getLocalAddress)
      case TcpAccepted(_, _)       => latch.countDown()
      case _                       => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def echo(): Unit = {

    val message = "Hello NIO!"
    val latch = new CountDownLatch(2)
    def count = latch.getCount

    lazy val loop: SelectorLoop = SelectorLoop("echo") {
      case TcpBound(serverChannel)              => loop.connect(serverChannel.getLocalAddress)
      case TcpConnected(channel)                => loop.write(channel, message)
      case TcpData(channel, data) if count == 2 => loop.write(channel, data); latch.countDown()
      case TcpData(channel, data)               => if (message == new String(data)) latch.countDown()
      case _                                    => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  /**
   * Verify that channel is only closed after last (pending)
   * write operation was completed.
   */
  @Test def write$close(): Unit = {
    val latch = new CountDownLatch(1)
    val buffer = new Array[Byte](8192)
    var dataReceived = 0
    val dataExpected = 8 * 8192

    lazy val loop: SelectorLoop = SelectorLoop("write$close") {
      case TcpBound(serverChannel) => loop.connect(serverChannel.getLocalAddress)

      case TcpConnected(channel) =>
        // immediately issue close request after last write:
        (1 to 8).foreach { i => loop.write(channel, buffer) }
        loop.close(channel)

      case TcpData(channel, data) =>
        dataReceived += data.length
        if (dataExpected == dataReceived) latch.countDown()

      case unhandled => /* ignore */
    }

    bindAndWait(loop, latch).close()
  }

  @Test def client$suspend(): Unit = {
    // Expect DrainInd() from server and all data received for client.
    val latch = new CountDownLatch(2)
    val writeBufferSize = 2 * 1024 * 1024 // should lead to write buffer overflow

    val writeBuffer = Array.fill(writeBufferSize)(' '.asInstanceOf[Byte])
    var bytesReceived: Int = 0
    val scheduler = Executors.newScheduledThreadPool(8)
    val resumeLatch = new CountDownLatch(1)

    lazy val loop: SelectorLoop = SelectorLoop("client$suspend") {
      case TcpBound(acceptor)            => loop.connect(acceptor.getLocalAddress)
      case TcpAccepted(acceptor, server) => schedule(sendTask(server))
      case TcpConnected(client)          => loop.suspend(client); schedule(resumeTask(client))
      case TcpOverflow(_)                => resumeLatch.countDown()
      case TcpDrain(_)                   => latch.countDown()
      case TcpData(_, buffer)            => received(buffer)
      case _                             => /* ignore */
    }

    def schedule(task: Runnable, delay: Long = 0L) = scheduler.schedule(task, delay, TimeUnit.MILLISECONDS)
    def sendTask(server: SocketChannel) = () => loop.write(server, writeBuffer)

    def resumeTask(client: SocketChannel) = () => {
      resumeLatch.await()
      loop.resume(client)
    }

    def received(buffer: Array[Byte]): Unit = {
      bytesReceived += buffer.length
      if (writeBufferSize == bytesReceived) latch.countDown()
    }

    bindAndWait(loop, latch).close()
  }

  // ==> helpers and stuff...

  private def await(latch: CountDownLatch): Unit = {
    latch.await(defaultTimeout, TimeUnit.MILLISECONDS)
    Assert.assertEquals(s"wait timed out after $defaultTimeout ms.", 0, latch.getCount)
  }

  private def bindAndWait(loop: SelectorLoop, latch: CountDownLatch): SelectorLoop = {
    loop.bind(null)
    latch.await(1L, TimeUnit.SECONDS)
    Assert.assertEquals("unexpected latch counter.", 0, latch.getCount)
    loop
  }

  private val loggingDispatch: PartialFunction[TcpEvent, Any] = {
    case event => logger.debug("received event: " + event)
  }
}
