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

import org.slf4j.LoggerFactory
import scala.collection.mutable
import io.bitbang.Combinators

/**
 * Double linked list like data structure, which manages an ordered list of [[Layer]]s.
 * Imagine a vertically aligned stack of layers, where messages travel in either
 * upstream or downstream direction.
 *
 * NOTE: Layers are added from bottom to top.
 *
 * @author Horst Dehmer (horst.dehmer@syncpoint.io)
 */
final class Pipeline { pipeline =>

  private val logger = LoggerFactory.getLogger(classOf[Pipeline])

  private implicit class LoggingContext(c: Context) {
    def warn(m: String) = logger.warn(message(m))
    def debug(m: String) = logger.debug(message(m))
    def trace(m: String) = logger.trace(message(m))
    @inline private def message(m: String) = s"[${c.name}] $m"
  }

  /**
   * Ordered list of layer contexts.
   * `head` = 'bottom layer of the pipeline', `last` = 'top layer of the pipeline'. 
   */
  private val contexts = mutable.ListBuffer[Context]()
  
  /** Layer lookup through contexts. */
  private val layers = mutable.Map[Context, Layer]()

  // START - PUBLIC API.

  /**
   * Adds a new layer above the layer which is currently placed at the top of the pipeline.
   * NOTE: Pipelines grow from bottom (i.e. head) to top (i.e. last).
   */
  def addLast(name: String, layer: Layer) {
    assert(contexts.forall(context => context.name != name), "layer name is not unique within pipeline.")

    createContext(name) K { context =>
      contexts.append(context)
      layers += (context -> layer)
    }
  }

  /**
   * Passes a new message from the bottom into the pipeline.
   *
   * @param message Message to be handled by the bottom most layer's upstream message handler.
   */
  def received(message: AnyRef) {
    if (contexts.nonEmpty) contexts.head K (c => layers(c).handleUpstream(c)(message))
    else throw new IllegalStateException("pipeline is empty; message not delivered: " + message)
  }

  /**
   * Passes a new message from the top into the pipeline.
   *
   * @param message Message to be handled by the top most layer's downstream message handler.
   */
  def send(message: AnyRef) {
    if (contexts.nonEmpty) contexts.last K (c => layers(c).handleDownstream(c)(message))
    else throw new IllegalStateException("pipeline is empty; message not delivered: " + message)
  }

  var deadMessageHandler: (Context, AnyRef) => Any = (sender, message) => {
    sender.warn(s"receiving layer undefined for message: $message")
  }

  // END - PUBLIC API.

  private def createContext(name: String) = new Context(name) { context =>
    override def sendUpstream(message: AnyRef) = pipeline.sendUpstream(context, message)
    override def sendDownstream(message: AnyRef) = pipeline.sendDownstream(context, message)
    override def unhandledMessage(message: AnyRef) = context.debug(s"unhandled message: $message")
  }

  private def sendUpstream(sender: Context, message: AnyRef) {
    sender.trace(s"sending upstream message: $message")
    contexts.indexOf(sender) K { index =>
      if (index + 1 < contexts.size) contexts(index + 1) K (c => layers(c).handleUpstream(c)(message))
      else deadMessageHandler(sender, message)
    }
  }

  private def sendDownstream(sender: Context, message: AnyRef) {
    sender.trace(s"sending downstream message: $message")
    contexts.indexOf(sender) K { index =>
      if (index - 1 >= 0) contexts(index - 1) K (c => layers(c).handleDownstream(c)(message))
      else deadMessageHandler(sender, message)
    }
  }
}
