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

import java.util.concurrent.atomic.AtomicLong
import java.util.{Timer, TimerTask}

import io.bitbang.pipeline.{Context, MessageHandler, UpstreamLayer}
import io.bitbang.Combinators

case class Release(buffer: Array[Byte])

/**
 * Experimental implementation of token bucket algorithm for bandwidth limiting.
 *
 * @param timer Timer used to schedule refill task.
 * @param delta Number of tokens added to the bucket on each refill.
 * @param period Period of refill task.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
class TokenBucket(timer: Timer, delta: Int, period: Long) extends UpstreamLayer {
  private val tokens                       = new AtomicLong(0L)
  private var blocked: Option[Array[Byte]] = None
  private var task   : TimerTask           = _

  override def handleUpstream(context: Context): MessageHandler = {
    case SuspendInd => /* ignore */
    case ResumedInd => /* ignore */

    case OpenInd =>
      // Change default buffer size:
      context.sendDownstream(BufferSizeReq(2 * delta))
      context.sendUpstream(OpenInd)
      task = task(context) K (task => timer.schedule(task, period, period))

    case CloseInd =>
      context.sendUpstream(CloseInd)
      task.cancel()

    case DataInd(buffer) if isConformant(buffer) => conformant(context, buffer)
    case DataInd(buffer)                         => nonConformant(context, buffer)
    case Release(buffer)                         => release(context, buffer)
    case unhandled                               => context.sendUpstream(unhandled)
  }

  private def isConformant(buffer: Array[Byte]) = tokens.get() >= buffer.length
  private def conformant(context: Context, buffer: Array[Byte]): Unit = {
    tokens.addAndGet(-buffer.length)
    context.sendUpstream(DataInd(buffer))
  }

  private def nonConformant(context: Context, buffer: Array[Byte]): Unit = {
    blocked = Some(buffer)
    context.sendDownstream(SuspendReq)
  }

  private def release(context: Context, buffer: Array[Byte]): Unit = {
    blocked = None
    tokens.addAndGet(-buffer.length)
    context.sendUpstream(DataInd(buffer))
    context.sendDownstream(ResumeReq)
  }

  /**
   * Periodical refill task.
   */
  private def task(context: Context): TimerTask = new TimerTask {
    override def run(): Unit = {
      val available = tokens.addAndGet(delta)
      blocked.foreach(buffer => {
        if (available >= buffer.length) context.sendDownstream(MessageReq(Release(buffer)))
      })
    }
  }
}
