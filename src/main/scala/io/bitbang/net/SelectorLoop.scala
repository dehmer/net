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

import java.io.{Closeable, File}
import java.net.SocketAddress
import java.nio.channels.SelectionKey._
import java.nio.channels.{ServerSocketChannel => SSC, SocketChannel => SC, _}
import java.nio.charset.{Charset, StandardCharsets}
import java.util
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

object SelectorLoop {

  /** Loop is running and accepts tasks for execution. */
  private val RUNNING = 1 << 0

  /** Loop is in the process of being disposed. */
  private val DISPOSING = 1 << 1

  /** Loop is disposed and no longer accepts tasks for execution (end state). */
  private val DISPOSED = 1 << 2

  /** Used to give each selector loop instance a unique number. */
  private val SequenceNo        = new AtomicInteger(1)
  private val DefaultBufferSize = 64 * 1024

  /**
   * Default read buffer; initially used for each channel.
   */
  private val DefaultReadBuffer = new ReadBuffer(DefaultBufferSize)

  def apply(name: String)(dispatch: PartialFunction[TcpEvent, Any]) = {
    new SelectorLoop(name, dispatch)
  }
}

/**
 * Single-threaded NIO TCP selector loop for managing an arbitrary number of server and client
 * socket channels.
 * NOTE: All events are dispatched on the selector thread.
 *
 * @param name Base name for underlying selector thread.
 * @param dispatch Callback to receive events emitted during the selection process.
 */
final class SelectorLoop(name: String, dispatch: PartialFunction[TcpEvent, Any]) extends Closeable {

  import SelectorLoop._
  import WriteOperations._

  /** Type of handlers attached to channel registrations. */
  private type ChannelHandler = (SelectionKey) => Any

  // ==> SHARED STATE

  /** One selector for all channels. */
  private val selector = Selector.open()
  private val state    = new AtomicInteger(RUNNING)

  /**
   * By default, a single default read buffer is shared among all channels.
   * When a new read buffer size is set explicitly for a channel a 'private' buffer
   * is created and stored here.
   */
  private val readBuffers = mutable.Map[SelectableChannel, ReadBuffer]()

  /** Queue/list of pending writes per channel. */
  private val pendingWrites          = new WriteOperations
  private var selectorThread: Thread = _

  /**
   * Single-threaded executor to handle all I/O operations and other
   * management tasks, which deal with possibly shared state or API which
   * otherwise is not thread-safe.
   *
   * CAUTION:
   * If a thread dies (terminates) a new thread is created to replace it.
   * This is usually an indication for a programming error, where some
   * event handler does not deal with unhandled exceptions.
   */
  private val taskQueue = Executors.newSingleThreadExecutor(new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      selectorThread = Executors.defaultThreadFactory().newThread(r)
      selectorThread.setName(name + "-" + SequenceNo.getAndIncrement)
      selectorThread
    }
  })

  /**
   * Self-scheduling select task which drives the selection process.
   */
  private lazy val selectTask: () => Any = () => if (selector.isOpen) {
    try if (selector.select() > 0) process(selector.selectedKeys().iterator())
    finally taskQueue.execute(selectTask)
  }

  private val disposeTask = () => {
    state.set(DISPOSED)
    selector.keys().filter(_.isValid).map(_.channel).foreach(closeChannel)
    selector.close()

    // Hand-off shutdown to another thread:
    Future(taskQueue.shutdownGracefully(100L, TimeUnit.MILLISECONDS))
  }

  // <== SHARED STATE

  // Get the party started:
  taskQueue.execute(selectTask)

  // ==> PUBLIC API
  // The public API can be called from ANY thread at ANY time (even concurrently).
  // Thus, all functions and procedures are reentrant and thread-safe.

  /**
   * Binds a server socket channel to the specified local endpoint.
   * After the channel is bound, it accepts client connections.
   *
   * @param endpoint Local address to bind to (might be <code>null</code>).
   * @param f Callback which is called immediately (but asynchronously) after the channel was created.
   * @return Future which is completed after the channel is ready to accept connections.
   */
  def bind(endpoint: SocketAddress, f: SSC => Any = channel => {}): Future[SSC] = {
    val p = Promise[SSC]()
    asyncIO(SSC.open(), p) { channel =>
      f(channel)
      channel.configureBlocking(false)
      channel.bind(endpoint)
      register(channel, OP_ACCEPT, acceptHandler)
      dispatch(TcpBound(channel))
      p.success(channel)
    }

    p.future
  }

  /**
   * Connects a (client) socket channel to the specified remote endpoint.
   * After the connection was established, the channel waits for data to read from the socket.
   *
   * @param endpoint Remote address to connect to.
   * @param f Callback which is called immediately (but asynchronously) after the channel was created.
   * @return Future which is completed after the connection was successfully established.
   */
  def connect(endpoint: SocketAddress, f: SC => Any = channel => {}): Future[SC] = {
    val p = Promise[SC]()
    asyncIO(SC.open, p) { channel =>
      f(channel)
      channel.configureBlocking(false)
      register(channel, OP_CONNECT, connectHandler(p))
      channel.connect(endpoint)
    }

    p.future
  }

  def write(channel: SC, s: String, charset: Charset = StandardCharsets.UTF_8): Unit = {
    val buf = s.getBytes(charset)
    write(channel, writeBufferOp(buf, 0, buf.length))
  }

  def write(channel: SC, buf: Array[Byte]): Unit = {
    write(channel, writeBufferOp(buf, 0, buf.length))
  }

  def write(channel: SC, buf: Array[Byte], off: Int, len: Int): Unit = {
    write(channel, writeBufferOp(buf, off, len))
  }

  def write(channel: SC, file: File, off: Long, len: Long): Unit = {
    write(channel, writeFileOp(file, off, len))
  }

  /**
   * Explicitly sets read buffer size for the given channel.
   */
  def bufferSize(channel: SC, size: Int): Unit = {
    assert(size >= 0, s"invalid read buffer size: $size")
    if (DefaultBufferSize != size) {
      execute(() => readBuffers += (channel -> new ReadBuffer(size)))
    }
  }

  /**
   * Closes the given channel.
   * If there are pending write operations, the channel is only
   * closed after the last write operation has been completed.
   */
  def close(channel: SelectableChannel): Unit = {
    asyncIO(channel) { channel =>
      // Immediately close the channel if there are no pending write operations:
      if (pendingWrites.isEmpty(channel)) closeChannel(channel)
      else {
        // Close channel after the last write operations was completed:
        val continuation = (key: SelectionKey) => closeChannel(channel)
        register(channel, OP_WRITE, writeHandler(continuation))
      }
    }
  }

  /**
   * Suspend current interest in OP_READ or OP_WRITE.
   */
  def suspend(channel: SC): Unit = {
    asyncIO(channel) { channel =>
      val key = keyFor(channel)

      // Only suspend channel if current interest ops are set,
      // i.e. channel is currently not suspended.
      // Doing otherwise would make resumption impossible.
      if (key.interestOps() != 0) {
        updateKey(key, 0, resumeHandler(channel))
        dispatch(TcpSuspended(channel))
      }
    }
  }

  /**
   * Resume previous interest ops and handler.
   */
  def resume(channel: SC) = {
    asyncIO(channel) { channel =>
      val key = keyFor(channel)

      // Only resume if current interest ops are cleared.
      // Immediately apply key to resume handler to re-register previous handler.
      if (key.interestOps() == 0) handler(key)(key)
    }
  }

  /**
   * Dispatches a user-defined message through selector thread.
   */
  def receive(channel: SC, message: AnyRef): Unit = {
    asyncIO(channel)(channel => dispatch(TcpReceived(channel, message)))
  }

  override def close(): Unit = {
    if (state.compareAndSet(RUNNING, DISPOSING)) {
      execute(disposeTask)
    }
  }

  // <== PUBLIC API

  // ==> Channel Handlers.
  // Note: Handlers are guaranteed to be executed from within selector thread.

  private def write(channel: SC, op: WriteOperation): Unit = {
    asyncIO(channel) { channel =>
      pendingWrites.append(channel, op)
      // Switch back to OP_READ after the last write operation was completed:
      val continuation = (key: SelectionKey) => updateKey(key, OP_READ, readHandler)
      register(channel, OP_WRITE, writeHandler(continuation))
    }
  }

  private val readHandler: ChannelHandler = (key) => {
    io(channel(key)) { channel =>
      // Use 'private' read buffer if available, or default buffer used for all channels:
      val buffer = readBuffers.getOrElse(channel, DefaultReadBuffer)
      buffer.read(channel) match {
        case Some(data) => dispatch(TcpData(channel, data))

        // Connection reset by peer (stream closed); close our side too:
        case None => dispatch(TcpEnd(channel)); closeChannel(channel)
      }
    }
  }

  private def writeHandler(continuation: (SelectionKey) => Any): ChannelHandler = (key) => {
    io(channel(key)) { channel =>
      pendingWrites.flush(channel) match {
        case true  => dispatch(TcpDrain(channel)); continuation(key)
        case false => dispatch(TcpOverflow(channel))
      }
    }
  }

  private val acceptHandler: ChannelHandler = (key) => {
    io(serverChannel(key)) { serverChannel =>
      io(serverChannel.accept()) { channel =>
        channel.configureBlocking(false)
        register(channel, OP_READ, readHandler)
        dispatch(TcpAccepted(serverChannel, channel))
      }
    }
  }

  private def connectHandler(p: Promise[SC]): ChannelHandler = (key) => {
    io(channel(key)) { channel =>
      if (channel.finishConnect) {
        updateKey(key, OP_READ, readHandler)
        dispatch(TcpConnected(channel))
        p.success(channel)
      }
    }
  }

  private def resumeHandler(channel: SC): ChannelHandler = {

    // Store current interest ops and handler for later resumption.
    val (currentOps, currentHandler) = {
      val key = keyFor(channel)
      (key.interestOps(), handler(key))
    }

    // Re-register the original handler under previous interest ops.
    // NOTE: This handler is invoked 'manually' in resume().
    (key) => io(channel) { channel =>
      updateKey(key, currentOps, currentHandler)
      dispatch(TcpResumed(channel))
    }
  }

  // <== Channel Handlers.

  @tailrec
  private def process(keys: util.Iterator[SelectionKey]): Unit = {
    if (keys.hasNext) {
      val key = keys.next()
      keys.remove()
      if (key.isValid) handler(key)(key)
      process(keys)
    }
  }

  @inline private def serverChannel(key: SelectionKey) = key.channel.asInstanceOf[SSC]
  @inline private def channel(key: SelectionKey) = key.channel.asInstanceOf[SC]
  @inline private def handler(key: SelectionKey) = key.attachment().asInstanceOf[ChannelHandler]

  @inline private def register(channel: SelectableChannel, ops: Int, handler: ChannelHandler): Unit = {
    channel.register(selector, ops, handler)
  }

  @inline private def updateKey(key: SelectionKey, ops: Int, handler: ChannelHandler): Unit = {
    key.interestOps(ops)
    key.attach(handler)
  }

  @inline private def keyFor(channel: SelectableChannel): SelectionKey = {
    val key = channel.keyFor(selector)
    if (key == null) throw new IllegalStateException(s"channel not registered with selector: $channel")
    else key
  }

  /**
   * Executes a given task on the selector thread.
   * The task is immediately executed if the current thread is the selector thread.
   * If the current thread is not the selector thread, the task is scheduled for
   * execution and the current selection process is 'interrupted'.
   */
  private def execute(task: () => Any): Unit = {
    if (DISPOSED == state.get()) throw new IllegalStateException("selector disposed.")

    if (Thread.currentThread() eq selectorThread) task()
    else {
      taskQueue.execute(task)
      selector.wakeup()
    }
  }

  /**
   * Executes some asynchronous I/O operation for a channel on the selector thread.
   */
  private def asyncIO[C <: SelectableChannel](channel: C)(f: (C) => Any) = {
    execute(() => io(channel)(f))
  }

  /**
   * Executes some asynchronous I/O operation for a channel on the selector thread.
   * Upon an (I/O) error, the supplied promise is completed with the
   * underlying exception.
   */
  private def asyncIO[C <: SelectableChannel](channel: C, p: Promise[_])(f: (C) => Any): Unit = {
    execute(() => io(channel)(f).foreach(cause => p.failure(cause)))
  }

  /**
   * Executes some I/O operation for a channel.
   * Upon an (I/O) error, a corresponding [[TcpError]] event is dispatched
   * and the channel is implicitly closed, freeing all its resources.
   *
   * @return Optional (I/O) error cause.
   */
  private def io[C <: SelectableChannel](channel: C)(f: (C) => Any): Option[Throwable] = {
    try {
      f(channel)
      None
    }
    catch {
      case NonFatal(exception) =>
        dispatch(TcpError(channel, exception))
        closeChannel(channel)
        Some(exception)
    }
  }

  private def closeChannel(channel: SelectableChannel): Unit = {
    readBuffers -= channel
    pendingWrites.drop(channel)

    // Cancels all keys and hopefully allows for attachments to be GCed too:
    channel.close()
    dispatch(TcpClosed(channel))
  }
}