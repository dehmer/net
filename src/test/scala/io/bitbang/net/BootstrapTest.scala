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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import org.junit.{Assert, Test}

import io.bitbang.pipeline._
import io.bitbang.{Combinators, RichCloseable}

final class BootstrapTest {

  @Test def echo(): Unit = new Bootstrap("TCP").foreach { bootstrap =>
    val latch = new CountDownLatch(1)
    bootstrap.bind(null, serverPipeline).onComplete {
      case Failure(exception) => exception.printStackTrace()
      case Success(endpoint)  => bootstrap.connect(endpoint, clientPipeline(latch))
    }

    latch.await(500L, TimeUnit.MILLISECONDS)
    Assert.assertEquals("didn't receive expected reply.", 0, latch.getCount)
  }

  private def serverPipeline = {
    val logger = new LoggingLayer
    () => new Pipeline K { pipeline =>
      pipeline.addLast("SERVER-SOCKET", new SocketLayer)
      pipeline.addLast("LOGGING", logger)
      pipeline.addLast("SERVER", server)
    }
  }

  private def clientPipeline(latch: CountDownLatch)  = {
    val logger = new LoggingLayer
    () => new Pipeline K { pipeline =>
      pipeline.addLast("CLIENT-SOCKET", new SocketLayer)
      pipeline.addLast("LOGGING", logger)
      pipeline.addLast("CLIENT", client(latch))
    }
  }

  private val server = new UpstreamLayer {
    override def handleUpstream(context: Context): MessageHandler = {
      case DataInd(buffer) => context.sendDownstream(WriteStringReq("echo/" + new String(buffer)))
      case unhandled       => context.unhandledMessage(unhandled)
    }
  }

  private def client(latch: CountDownLatch) = new UpstreamLayer {
    override def handleUpstream(context: Context): MessageHandler = {
      case OpenInd         => context.sendDownstream(WriteStringReq("hello"))
      case DataInd(buffer) =>
        if ("echo/hello" == new String(buffer)) latch.countDown()
        context.sendDownstream(CloseReq)
      case unhandled       => context.unhandledMessage(unhandled)
    }
  }
}
