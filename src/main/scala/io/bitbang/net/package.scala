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

package io.bitbang

import java.util
import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

package object net {

  var EmptyBuffer = new Array[Byte](0)

  implicit class RichExecutorService(executor: ExecutorService) {
    def shutdownGracefully(gracePeriod: Long, unit: TimeUnit): Try[util.List[Runnable]] = {
      // Ask nicely:
      executor.shutdown()

      try {
        // Be more insistent if necessary:
        if (!executor.awaitTermination(gracePeriod, unit)) Success(executor.shutdownNow())
        else Success(util.Collections.emptyList[Runnable])
      }
      catch {
        case exception: InterruptedException =>
          // Restore thread's interrupted flag, which gets cleared by catching the exception.
          // See also: http://www.javamex.com/tutorials/threads/thread_interruption_2.shtml#InterruptedException
          Thread.currentThread().interrupt()
          Success(executor.shutdownNow())

        case NonFatal(exception) => Failure(exception)
      }
    }
  }

  implicit class RunnableWrapper(f: () => Any) extends Runnable {
    override def run(): Unit = f()
  }
}
