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
 * A (named) layer context is a sort of back-reference from a
 * layer to its owning pipeline.
 * Since one layer instance can be shared between different pipelines,
 * layers are tied to each other indirectly through contexts.
 * Seen differently, pipelines manage layer contexts,
 * and thus only indirectly layers.
 *
 * @param name Name under which a layer is registered with a pipeline.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
abstract class Context(val name: String) {

  /** Sends a message to the layer above the layer of this context. */
  def sendUpstream(message: AnyRef): Unit

  /** Sends a message to the layer below the layer of this context. */
  def sendDownstream(message: AnyRef): Unit

  /** Explicitly marks a message as unhandled. */
  def unhandledMessage(message: AnyRef): Unit
}
