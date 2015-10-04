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

import scala.util.matching.Regex
import scala.util.matching.Regex.Match
import io.bitbang.http._

/**
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
final class Route(path: String, val handler: Handler) {
  case class Key(name: String, optional: Boolean)
  private var keys         = List[Key]()
  private val regex: Regex = regex(path)

  def matches(path: String): Boolean = {
    regex.findFirstMatchIn(path).isDefined
  }

  def extractParams(request: Request): Request = {
    regex.findFirstMatchIn(request.path) match {
      case None    => /* ignore */
      case Some(m) => keys.map(k => k.name).foreach { key =>
        request.addParam(key, m.group(key))
      }
    }

    request
  }

  private def regex(path: String) = {
    val replacer: (Match) => String = m => {
      def slash = if (m.group("slash") == null) "" else "/"
      def optional = m.group("optional") != null
      def key = m.group("key")
      def capture = m.group("capture")
      def captureOrWildcard = if (capture != null) capture else "([^/.]+?)"

      // add current key name to recognized keys:
      keys = Key(key, optional) :: keys

      (if (optional) "" else slash) +
        "(?:" +
        (if (optional) slash else "") +
        captureOrWildcard +
        ")" +
        (if (optional) "?" else "")
    }

    val pattern = {
      val regex = """(\/)?:(\w+)(?:(\(.*?\)))?(\?)?"""
      val groupNames = List("slash", "key", "capture", "optional")
      new Regex(regex, groupNames: _*).replaceAllIn(path, replacer)
    }

    new Regex("^" + pattern + "$", keys.reverse.map(_.name): _*)
  }
}
