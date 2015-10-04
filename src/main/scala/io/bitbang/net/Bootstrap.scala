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

import java.io.{Closeable, File}
import java.net.SocketAddress
import java.nio.channels.{ServerSocketChannel => SSC, SocketChannel => SC}
import java.nio.charset.Charset
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.bitbang.pipeline.Pipeline
import io.bitbang.Combinators

/**
 * Glue between low-level selector loop events and pipeline abstraction.
 * Associates client/server pipelines with a selector loop.
 *
 * @param name Name prefix for selector loop threads.
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
final class Bootstrap(name: String) extends Closeable {
  private val pfs = mutable.Map[SSC, () => Pipeline]()
  private val ps  = mutable.Map[SC, Pipeline]()

  private val loop = SelectorLoop(name) {
    case TcpBound(ssc)             => /* ignore */
    case TcpAccepted(ssc, sc)      => ps += (sc -> (pfs(ssc)() K (_.received(SoOpen(socket(sc))))))
    case TcpClosed(sc: SC)         => ps(sc).received(SoClose); ps -= sc
    case TcpClosed(ssc: SSC)       => pfs -= ssc
    case TcpEnd(sc)                => ps(sc).received(SoEnd)
    case TcpError(sc: SC, cause)   => ps(sc).received(SoError(cause))
    case TcpError(ssc: SSC, cause) => cause.printStackTrace()
    case TcpConnected(sc)          => ps(sc).received(SoOpen(socket(sc)))
    case TcpData(sc, buffer)       => ps(sc).received(SoData(buffer))
    case TcpDrain(sc)              => ps(sc).received(SoDrain)
    case TcpOverflow(sc)           => ps(sc).received(SoOverflow)
    case TcpSuspended(sc)          => ps(sc).received(SoSuspended)
    case TcpResumed(sc)            => ps(sc).received(SoResumed)
    case TcpReceived(sc, message)  => ps(sc).received(SoReceived(message))
  }

  def bind(endpoint: SocketAddress, pf: () => Pipeline): Future[SocketAddress] = {
    loop.bind(endpoint, ssc => pfs += (ssc -> pf)).map(ssc => ssc.getLocalAddress)
  }

  def connect(endpoint: SocketAddress, pf: () => Pipeline): Future[(AnyRef) => Any] = {
    loop.connect(endpoint, sc => ps += (sc -> pf())).map { sc =>
      (message: AnyRef) => ps(sc).send(message)
    }
  }

  def close(): Unit = loop.close()

  private def socket(sc: SC): Socket = new Socket {
    override def bufferSize(size: Int): Unit = loop.bufferSize(sc, size)
    override def write(buf: Array[Byte], off: Int, len: Int) = loop.write(sc, buf, off, len)
    override def write(s: String, charset: Charset): Unit = loop.write(sc, s, charset)
    override def write(file: File, off: Int, len: Int) = loop.write(sc, file, off, len)
    override def suspend(): Unit = loop.suspend(sc)
    override def resume(): Unit = loop.resume(sc)
    override def receive(message: AnyRef): Unit = loop.receive(sc, message)
    override def close(): Unit = loop.close(sc)
  }
}
