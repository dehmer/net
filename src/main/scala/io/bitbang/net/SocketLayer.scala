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

package io.bitbang.net

import io.bitbang.pipeline.{Context, Layer, MessageHandler}

/**
 * Handles up-/downstream communication between a pipeline
 * and the selector loop.
 * This layer is typically the bottom most layer in a pipeline.
 *
 * @author <a href="mailto:horst.dehmer@snycpoint.io">Horst Dehmer</a>
 */
final class SocketLayer extends Layer {
  private var socket: Socket = _

  /**
   * Translates [[SocketEvent]] to [[SocketIndication]].
   *
   * @param context Context of the layer in a given pipeline.
   * @return Partial function, which can or can not handle the current upstream message.
   */
  override def handleUpstream(context: Context): MessageHandler = {
    case SoOpen(s)           => this.socket = s; context.sendUpstream(OpenInd)
    case SoData(buffer)      => context.sendUpstream(DataInd(buffer))
    case SoEnd               => context.sendUpstream(EndInd)
    case SoClose             => context.sendUpstream(CloseInd)
    case SoError(exception)  => context.sendUpstream(ErrorInd(exception))
    case SoDrain             => context.sendUpstream(DrainInd)
    case SoOverflow          => context.sendUpstream(OverflowInd)
    case SoSuspended         => context.sendUpstream(SuspendInd)
    case SoResumed           => context.sendUpstream(ResumedInd)
    case SoReceived(message) => context.sendUpstream(message)
    case unhandled           => context.unhandledMessage(unhandled)
  }

  /**
   * Translates [[SocketRequest]] to socket (i.e. selector loop) calls.
   *
   * @param context Context of the layer in a given pipeline.
   * @return Partial function, which can or can not handle the current downstream message.
   */
  override def handleDownstream(context: Context): MessageHandler = {
    case BufferSizeReq(size)                    => socket.bufferSize(size)
    case WriteBufferReq(buffer, offset, length) => socket.write(buffer, offset, length)
    case WriteStringReq(s, charset)             => socket.write(s, charset)
    case CloseReq                               => socket.close()
    case SuspendReq                             => socket.suspend()
    case ResumeReq                              => socket.resume()
    case MessageReq(message)                    => socket.receive(message)
    case unhandled                              => context.unhandledMessage(unhandled)
  }
}
