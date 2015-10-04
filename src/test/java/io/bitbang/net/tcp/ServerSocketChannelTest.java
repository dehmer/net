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

package io.bitbang.net.tcp;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.*;

import static java.nio.channels.SelectionKey.*;

public class ServerSocketChannelTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerSocketChannelTest.class);

    @Test
    public void channel$bind() {
        try (ServerSocketChannel channel = ServerSocketChannel.open()) {

            // Be as strict as possible about address reuse.
            // Note: Behavior is highly platform-dependent.
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, false);

            // Bind is always blocking. There is no use to configure channel for
            // non-blocking operations just yet.
            // Throws I/O, security and a bunch of unchecked exceptions:
            channel.bind(null);

            // Returns `null` if socket is not bound:
            SocketAddress localAddress = channel.getLocalAddress();
            Assert.assertNotNull("local address should be defined after bind.", localAddress);

        } catch (IOException exception) {
            LOGGER.warn("error opening server socket channel.", exception);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void channel$rebind() throws ClosedChannelException {
        try (ServerSocketChannel channel = ServerSocketChannel.open()) {

            // Be as strict as possible about address reuse.
            // Note: Behavior is highly platform-dependent.
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, false);

            // Bind is always blocking. There is no use to configure channel for
            // non-blocking operations just yet.
            // Throws I/O, security and a bunch of unchecked exceptions:
            channel.bind(null);

            // Returns `null` if socket is not bound:
            SocketAddress localAddress = channel.getLocalAddress();
            Assert.assertNotNull("local address should be defined after bind.", localAddress);

            // Try to bind again after close. Should raise ClosedChannelException.
            channel.close();
            channel.bind(null);
        } catch (ClosedChannelException rethrow) {
            throw rethrow;
        } catch (IOException exception) {
            LOGGER.warn("error opening server socket channel.", exception);
        }
    }

    @Test
    public void channel$close() throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();

        // Closing the channel seems to implicitly cancel its registrations keys.
        // See `AbstractSelectableChannel.implCloseChannel()`.
        // Cancelling a registrations key, in turn, removes the key from its selector.
        // Or more correctly, schedules it for 'garbage collection' (cancelled-key set)
        // on the next select operation.
        try {
            channel.close();
        } catch (IOException exception) {
            // IOException - If an I/O error occurs
            LOGGER.warn("error closing channel.", exception);
        }
    }

    @Test
    public void channel$validOps() {
        try (SelectableChannel channel = ServerSocketChannel.open()) {
            int expectedOps = OP_ACCEPT;
            Assert.assertEquals("unexpected `valid-ops` for server socket channel.", expectedOps, channel.validOps());
        } catch (IOException exception) {
            LOGGER.warn("error opening socket channel.", exception);
        }
    }

    @Test
    public void channel$register() {
        try (Selector selector = Selector.open();
             ServerSocketChannel channel = ServerSocketChannel.open()) {

            channel.setOption(StandardSocketOptions.SO_REUSEADDR, false);
            channel.bind(null);

            // Basically the same as for `SocketChannel`:
            channel.configureBlocking(false);

            // Note: Possible 'bound' callbacks should only be called AFTER
            // the channel was successfully registered with its selector.
            // Because, technically speaking, the channel is not yet accepting
            // new connections.
            SelectionKey key = channel.register(selector, OP_ACCEPT);
            Assert.assertTrue(key.isValid());
            Assert.assertEquals(OP_ACCEPT, key.interestOps());

        } catch (IOException exception) {
            LOGGER.warn("error registering server socket channel.", exception);
        }
    }
}
