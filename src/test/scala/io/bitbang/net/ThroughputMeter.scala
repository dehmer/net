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

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.{Timer, TimerTask}
import org.slf4j.LoggerFactory

import io.bitbang.pipeline.{Context, Layer}

/**
 * Logs upstream data throughput.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
class ThroughputMeter(name: String, timer: Timer) extends Layer {
  import ThroughputMeter._
  private val logger   = LoggerFactory.getLogger(name + "-" + serialNumber.getAndIncrement())
  private val sent     = new AtomicLong(0L)
  private val received = new AtomicLong(0L)
  private val task     = new TimerTask {
    override def run(): Unit = {
      logger.debug("received: " + received.get() + " b/s, sent: " + sent.get() + " b/s")
      received.set(0L)
      sent.set(0L)
    }
  }

  timer.schedule(task, 1000L, 1000L)

  override def handleUpstream(context: Context) = {
    case event: DataInd => receive(context, event)
    case event          => context.sendUpstream(event)
  }

  override def handleDownstream(context: Context) = {
    case request: WriteBufferReq => send(context, request)
    case request                 => context.sendDownstream(request)
  }

  private def receive(context: Context, event: DataInd): Unit = {
    received.addAndGet(event.buffer.length)
    context.sendUpstream(event)
  }

  private def send(context: Context, request: WriteBufferReq): Unit = {
    sent.addAndGet(request.length)
    context.sendDownstream(request)
  }
}

object ThroughputMeter {
  val serialNumber = new AtomicInteger(0)
}
