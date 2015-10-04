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

package io.bitbang.pipeline


/**
 * Layers form the active parts of pipelines.
 * Their ordering and interaction define the actual protocols
 * implemented by pipelines.
 *
 * @author Horst Dehmer (horst.dehmer@syncpoint.io)
 */
trait Layer {

  /**
   * @param context Context of the layer in a given pipeline.
   * @return Partial function, which can or can not handle the current upstream message.
   */
  def handleUpstream(context: Context): MessageHandler

  /**
   * @param context Context of the layer in a given pipeline.
   * @return Partial function, which can or can not handle the current downstream message.
   */
  def handleDownstream(context: Context): MessageHandler
}

/**
 * Used to only handle downstream messages.
 * Upstream messages are unconditionally passed on to the next layer.
 */
abstract class DownstreamLayer extends Layer {
  final override def handleUpstream(context: Context) = {
    case message => context.sendUpstream(message)
  }
}

/**
 * Used to only handle upstream messages.
 * Downstream messages are unconditionally passed on to the next layer.
 */
abstract class UpstreamLayer extends Layer {
  final override def handleDownstream(context: Context) = {
    case message => context.sendDownstream(message)
  }
}
