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

package io.bitbang.http

import java.net.InetSocketAddress

import io.bitbang.net._
import io.bitbang.pipeline._
import io.bitbang.Combinators

object Server extends App {
  val bootstrap = new Bootstrap("TCP")

  class Session extends UpstreamLayer {
    // TODO: connection timeout?
    override def handleUpstream(context: Context) = {
      case dataInd: DataInd => context.sendUpstream(dataInd)
      case ErrorInd(exception) => exception.printStackTrace()
      case _                => /* ignore */
    }
  }

  // $ curl http://localhost:8080
  // $ curl http://localhost:8080/nodes
  // $ curl -XPOST -H"Content-Type: application/json" -d '{"nodeId": "120000000"}' http://localhost:8080/nodes
  // $ curl -XDELETE http://localhost:8080/nodes/123/connections/456/dp


  def expressHandler: Layer = new ExpressHandler K { express =>
    express.get("/", (request, response) => response.send("hey, there!\n"))
    express.get("/nodes", (request, response) => response.send("no nodes available.\n"))
    express.post("/nodes", (request, response) => response.send(request.body))

    express.delete("/nodes/:local/connections/:remote/:type(dr|dp)", (request, response) => {
      val local = request.param("local")
      val remote = request.param("remote")
      val direction = if (request.param("type") == "dr") " <- " else " -> "
      response.send("deleted: " + local + direction + remote + "\n")
    })
  }

  def pipeline = () => new Pipeline K { pipeline =>
    pipeline.addLast("socket", new SocketLayer)
    pipeline.addLast("logging", new LoggingLayer)
    pipeline.addLast("session", new Session)
    pipeline.addLast("encoder", new ResponseEncoder)
    pipeline.addLast("decoder", new RequestDecoder)
    pipeline.addLast("handler", new RequestHandler)
    pipeline.addLast("express", expressHandler)
  }

  bootstrap.bind(new InetSocketAddress(8080), pipeline)
}
