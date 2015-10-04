package io.bitbang.net

import java.util.concurrent.CountDownLatch
import java.util.{Timer, TimerTask}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import org.slf4j.LoggerFactory

import io.bitbang.pipeline._
import io.bitbang.{Combinators, RichCloseable}

object TokenBucketLimiterMain extends App {
  val logger = LoggerFactory.getLogger(TokenBucketLimiterMain.getClass)
  val timer  = new Timer

  // server-global throughput meter, counts bytes sent for all client connections:
  val serverMeter = new ThroughputMeter("SERVER", timer)

  val clientCount = 8
  val latch       = new CountDownLatch(2 * clientCount)

  new Bootstrap("TCP") foreach { bootstrap =>
    bootstrap.bind(null, serverFactory(latch)) onComplete {
      case Failure(exception) => exception.printStackTrace()
      case Success(endpoint)  => (1 to clientCount) foreach { limit =>
        // connect each client with different limit:
        bootstrap.connect(endpoint, clientFactory(limit))
      }
    }

    latch.await()
    bootstrap.close()
    timer.cancel()
  }

  private def serverFactory(latch: CountDownLatch) = () => new Pipeline K { pipeline =>
    pipeline.addLast("SOCKET", new SocketLayer)
    pipeline.addLast("METER", serverMeter)
    pipeline.addLast("SERVER", server(latch))
  }

  private def clientFactory(limit: Int) = () => new Pipeline K { pipeline =>
    pipeline.addLast("SOCKET", new SocketLayer)
    pipeline.addLast("TOKEN-BUCKET", new TokenBucket(timer, limit * 1024, 500L))
    pipeline.addLast("METER", new ThroughputMeter("CLIENT", timer))
    pipeline.addLast("CLIENT", client)
  }

  private def server(latch: CountDownLatch) = new UpstreamLayer {
    val sendBuffer              = new Array[Byte](8 * 1024)
    var task: Option[TimerTask] = None

    override def handleUpstream(context: Context) = {
      // control back-pressure:
      case OverflowInd              => task = cancelTask()
      case DrainInd if task.isEmpty => latch.countDown(); task = scheduleTask(context)
      case OpenInd                  => task = scheduleTask(context)
      case DrainInd                 => /* ignore */
      case CloseInd                 => /* ignore */
      case unhandled                => context.unhandledMessage(unhandled)
    }

    private def cancelTask(): Option[TimerTask] = {
      task.foreach(_.cancel())
      None
    }

    private def scheduleTask(context: Context): Option[TimerTask] = {
      Some(timerTask(context) K (task => timer.schedule(task, 100L, 100L)))
    }

    private def timerTask(context: Context) = new TimerTask {
      override def run(): Unit = {
        context.sendDownstream(WriteBufferReq(sendBuffer))
      }
    }
  }

  private def client = new UpstreamLayer {
    override def handleUpstream(context: Context) = {
      case OpenInd    => /* ignore */
      case CloseInd   => /* ignore */
      case DataInd(_) => /* ignore */
      case unhandled  => context.unhandledMessage(unhandled)
    }
  }
}