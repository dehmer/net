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

import io.bitbang.Combinators
import scala.collection.JavaConversions

/**
 * Abstract factory function for named layers.
 * Note: Although pipelines can be created without using layer providers,
 * this API might be useful when using DI frameworks.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
trait LayerProvider extends (() => (String, Layer))

/**
 * Constructs a pipeline from a list of layer factories and/or references.
 * @param providers Ordered sequence of layer providers.
 */
final class SimplePipelineFactory(providers: Seq[LayerProvider]) extends (() => Pipeline) {

  /**
   * Converts a Java List to a Scala Sequence.
   * SEE ALSO: http://stackoverflow.com/questions/7716111/spring-injecting-a-scala-list
   * @param adapters Order Java list of adapters.
   */
  def this(adapters: java.util.List[LayerProvider]) {
    this(JavaConversions.asScalaBuffer(adapters))
  }

  override def apply() = new Pipeline K { pipeline =>
    providers.map(adapters => adapters()).foreach {
      case (name, layer) => pipeline.addLast(name, layer)
    }
  }
}

/**
 * Creates a new layer instance for each call.
 */
final class LayerFactory(name: String)(f: => Layer) extends LayerProvider {
  def apply(): (String, Layer) = (name, f)
}

/**
 * Returns the same (shared) layer instance for each call.
 * NOTE: This usually requires that the layer is stateless.
 */
final class LayerReference(name: String, layer: Layer) extends LayerProvider {
  def apply(): (String, Layer) = (name, layer)
}
