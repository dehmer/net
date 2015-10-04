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

package io

import java.io.Closeable
import java.util.concurrent.ExecutorService

/**
 * Collection of more or less useful helpers.
 */
package object bitbang {

  /**
   * K Combinator. See http://en.wikipedia.org/wiki/SKI_combinator_calculus
   *
   * @param v Value to return after side effects were applied.
   * @tparam T Value type.
   */
  implicit class Combinators[T](v: T) {

    /**
     * Applies the value to a side effect function `f` and returns the value as a result.
     * From 'Kestrel' (Falco tinnunculus).
     */
    def K[U](f: T => U): T = { f(v); v }

    /**
     * Thrush reverses the order of evaluation: Txy = yx
     * From 'Thrush' (Turdus).
     */
    def T[U](f: T => U) = f(v)
  }

  /** Automatic Resource Management (ARM), a.k.a. Loan Pattern. */
  implicit class RichCloseable[T <: Closeable](closeable: T) extends Traversable[T] {

    def foreach[U](f: T => U) = {
      try f(closeable)
      finally closeable.close()
    }
  }

  /** Automatic Resource Management (ARM), a.k.a. Loan Pattern. */
  implicit class RichAutoCloseable[T <: AutoCloseable](closeable: T) extends Traversable[T] {

    def foreach[U](f: T => U) = {
      try f(closeable)
      finally closeable.close()
    }
  }

  /** Allow for `() => Any` to be treated as [[Runnable]] */
  implicit class RunnableWrapper(f: () => Any) extends Runnable {
    override def run(): Unit = f()
  }

  /** Automatic Resource Management (ARM) like interface for [[ExecutorService]]. */
  implicit class RichExecutorService[E <: ExecutorService](executor: E) extends Traversable[E] {
    override def foreach[U](f: (E) => U): Unit = {
      try f(executor)
      // Don't fool around, just shut down /quiet /urgent /ring-the-bell
      finally executor.shutdownNow()
    }
  }

  def checkNotNull[T](reference: T): T = {
    if(reference == null) throw new NullPointerException
    reference
  }
}
