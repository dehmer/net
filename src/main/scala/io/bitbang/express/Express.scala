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

package io.bitbang.express

import java.net.{SocketAddress, InetSocketAddress}
import scala.concurrent.Future
import io.bitbang.http._
import io.bitbang.Combinators
import io.bitbang.net.{LoggingLayer, SocketLayer, Bootstrap}
import io.bitbang.pipeline.Pipeline

/**
 * @author Horst Dehmer
 */
final class Express {
  private val bootstrap = new Bootstrap("EXPRESS")
  private val routes = new ExpressHandler

  def get(path: String, handler: Handler) = routes.get(path, handler)
  def post(path: String, handler: Handler) = routes.post(path, handler)
  def delete(path: String, handler: Handler) = routes.delete(path, handler)

  def listen(port: Int): Future[SocketAddress] = {
    bootstrap.bind(new InetSocketAddress(port), pipeline)
  }

  def pipeline = () => new Pipeline K { pipeline =>
    pipeline.addLast("socket", new SocketLayer)
    pipeline.addLast("logging", new LoggingLayer)
    pipeline.addLast("encoder", new ResponseEncoder)
    pipeline.addLast("decoder", new RequestDecoder)
    pipeline.addLast("handler", new RequestHandler)
    pipeline.addLast("express", routes)
  }
}
