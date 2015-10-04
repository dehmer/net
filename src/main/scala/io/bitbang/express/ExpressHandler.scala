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

import io.bitbang.http._
import io.bitbang.pipeline.{Context, MessageHandler, UpstreamLayer}

/**
 * Experimental node.js/Express like HTTP request handler.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
class ExpressHandler extends UpstreamLayer {
  import ExpressHandler._
  private var map = Map[Method, List[Route]]()

  override def handleUpstream(context: Context): MessageHandler = {
    case request: Request => handle(context, request)
    case unhandled => context.unhandledMessage(unhandled)
  }

  def get(path: String, handler: Handler) = pushRoute(Method.Get, path, handler)
  def post(path: String, handler: Handler) = pushRoute(Method.Post, path, handler)
  def delete(path: String, handler: Handler) = pushRoute(Method.Delete, path, handler)

  private def handle(context: Context, request: Request): Unit = {
    map.
      getOrElse(request.method.toLowerCase, EmptyRoutes).
      filter(r => r.matches(request.path)).
      foreach(r => r.handler(r.extractParams(request), new Response(request.header, context)))
  }

  private def pushRoute(method: Method, path: String, handler: Handler) {
    val routes = map.getOrElse(method.toLowerCase, EmptyRoutes)
    map += (method.toLowerCase -> (new Route(path, handler) :: routes))
  }
}

object ExpressHandler {
  val EmptyRoutes = List[Route]()
}