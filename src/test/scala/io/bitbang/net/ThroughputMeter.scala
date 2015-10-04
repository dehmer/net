package io.bitbang.net

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.{Timer, TimerTask}
import org.slf4j.LoggerFactory

import io.bitbang.pipeline.{Context, Layer}

object ThroughputMeter {
  val serialNumber = new AtomicInteger(0)
}

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
