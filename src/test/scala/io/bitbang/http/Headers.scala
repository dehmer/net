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

/**
 * There are a few header fields which have general applicability for both request and
 * response messages, but which do not apply to the entity being transferred.
 * These header fields apply only to the message being transmitted.
 */
object GeneralHeader {
  val CacheControl     = "Cache-Control"
  val Connection       = "Connection"
  val Date             = "Date"
  val Pragma           = "Pragma"
  val Trailer          = "Trailer"
  val TransferEncoding = "Transfer-Encoding"
  val Upgrade          = "Upgrade"
  val Via              = "Via"
  val Warning          = "Warning"
}

/**
 * The request-header fields allow the client to pass additional information about the request,
 * and about the client itself, to the server. These fields act as request modifiers,
 * with semantics equivalent to the parameters on a programming language method invocation.
 */
object RequestHeader {
  val Accept             = "Accept"
  val AcceptCharset      = "Accept-Charset"
  val AcceptEncoding     = "Accept-Encoding"
  val AcceptLanguage     = "Accept-Language"
  val Authorization      = "Authorization"
  val Expect             = "Expect"
  val From               = "From"
  val Host               = "Host"
  val IfMatch            = "If-Match"
  val IfModifiedSince    = "If-Modified-Since"
  val IfNoneMatch        = "If-None-Match"
  val IfRange            = "If-Range"
  val IfUnmodifiedSince  = "If-Unmodified-Since"
  val MaxForwards        = "Max-Forwards"
  val ProxyAuthorization = "Proxy-Authorization"
  val Range              = "Range"
  val Referer            = "Referer"
  val TE                 = "TE"
  val UserAgent          = "User-Agent"
}

/**
 * The response-header fields allow the server to pass additional information about
 * the response which cannot be placed in the Status-Line. These header fields give
 * information about the server and about further access to the resource identified
 * by the Request-URI.
 */
object ResponseHeader {
  val AcceptRanges      = "Accept-Ranges"
  val Age               = "Age"
  val ETag              = "ETag"
  val Location          = "Location"
  val ProxyAuthenticate = "Proxy-Authenticate"
  val RetryAfter        = "Retry-After"
  val Server            = "Server"
  val Vary              = "Vary"
  val WWWAuthenticate   = "WWW-Authenticate"
}

/**
 * Entity-header fields define meta-information about the entity-body or, if no body is present,
 * about the resource identified by the request. Some of this meta-information is OPTIONAL;
 * some might be REQUIRED by portions of this specification.
 */
object EntityHeader {
  val Allow           = "Allow"
  val ContentEncoding = "Content-Encoding"
  val ContentLanguage = "Content-Language"
  val ContentLength   = "Content-Length"
  val ContentLocation = "Content-Location"
  val ContentMD5      = "Content-MD5"
  val ContentRange    = "Content-Range"
  val ContentType     = "Content-Type"
  val Expires         = "Expires"
  val LastModified    = "Last-Modified"
}

object Token {
  val Chunked = "chunked"
  val Close = "close"
}