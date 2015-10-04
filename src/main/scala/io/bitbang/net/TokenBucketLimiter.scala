package io.bitbang.net

import java.util.concurrent.atomic.AtomicLong
import java.util.{Timer, TimerTask}

import io.bitbang.pipeline.{Context, MessageHandler, UpstreamLayer}
import io.bitbang.Combinators

case class Release(buffer: Array[Byte])

class TokenBucket(timer: Timer, delta: Int, period: Long) extends UpstreamLayer {
  private val tokens                       = new AtomicLong(0L)
  private var blocked: Option[Array[Byte]] = None
  private var task   : TimerTask           = _

  override def handleUpstream(context: Context): MessageHandler = {
    case SuspendInd => /* ignore */
    case ResumedInd => /* ignore */

    case OpenInd =>
      // Decrease default buffer size:
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

  private def task(context: Context): TimerTask = new TimerTask {
    override def run(): Unit = {
      val available = tokens.addAndGet(delta)
      blocked.foreach(buffer => {
        if (available >= buffer.length) context.sendDownstream(MessageReq(Release(buffer)))
      })
    }
  }
}
