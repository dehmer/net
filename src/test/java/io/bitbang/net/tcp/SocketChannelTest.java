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
import java.nio.channels.*;
import java.util.Iterator;

import static java.nio.channels.SelectionKey.*;

/**
 * Java-style unit test to more easily detect and document
 * possible error conditions.
 * <p>
 * In general, the NIO API is not overly verbose about possible error
 * conditions (checked or runtime).
 * A common example: "IOException - If an I/O error occurs".
 */
public class SocketChannelTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelTest.class);

    @Test
    public void channel$open() {
        try (SocketChannel channel = SocketChannel.open()) {
            Assert.assertTrue("channel should now be open.", channel.isOpen());
        } catch (IOException exception) {
            // Javadoc:
            // IOException - If an I/O error occurs
            LOGGER.warn("error opening socket channel.", exception);
            Assert.fail(exception.getMessage());
        }
    }

    @Test
    public void channel$configureBlocking() {
        try (SocketChannel channel = SocketChannel.open()) {

            // Besides generic I/O exceptions, also throws IllegalBlockingModeException (a runtime exception).
            channel.configureBlocking(false);
            Assert.assertFalse("channel should now be non-blocking.", channel.isBlocking());
        } catch (IOException exception) {
            // Javadoc:
            // IOException - If an I/O error occurs
            LOGGER.warn("error opening socket channel for non-blocking operations.", exception);
            Assert.fail(exception.getMessage());
        }
    }

    @Test
    public void channel$validOps() {
        try(SelectableChannel channel = SocketChannel.open()) {
            int expectedOps = OP_CONNECT | OP_READ | OP_WRITE;
            Assert.assertEquals("unexpected `valid-ops` for socket channel.", expectedOps, channel.validOps());
        } catch (IOException exception) {
            LOGGER.warn("error opening socket channel.", exception);
        }
    }

    @Test
    public void channel$register() {
        try (Selector selector = Selector.open();
             SelectableChannel channel = SocketChannel.open()) {

            channel.configureBlocking(false);

            // Throws also a bunch of unchecked illegal state/argument exceptions.
            SelectionKey key = channel.register(selector, OP_CONNECT);
            Assert.assertTrue(key.isValid());
            Assert.assertEquals(OP_CONNECT, key.interestOps());
        } catch (IOException exception) {
            // Javadoc:
            // IOException - If an I/O error occurs
            LOGGER.warn("error registering channel for OP_CONNECT.", exception);
            Assert.fail(exception.getMessage());
        }
    }

    @Test
    public void channel$connect() throws InterruptedException {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open();
             SocketChannel client = SocketChannel.open()) {

            server.bind(null);
            server.configureBlocking(false);
            server.register(selector, OP_ACCEPT);

            client.configureBlocking(false);
            client.register(selector, OP_CONNECT);
            client.connect(server.getLocalAddress());

            // Give some time for OP_ACCEPT and OP_CONNECT to get through:
            Thread.sleep(50L);
            int readyCount = selector.selectNow();
            Assert.assertEquals(2, readyCount);
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while(keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if(!key.isValid()) continue;

                if(key.isAcceptable()) {
                    SocketChannel channel = server.accept();
                    LOGGER.debug("accepted connection: " + channel);
                    channel.configureBlocking(false);
                    channel.register(selector, OP_READ);
                } else if(key.isConnectable()) {
                    // Throws a whole slew of assorted I/O exceptions:
                    if(client.finishConnect()) LOGGER.debug("established connection: " + client);
                    else LOGGER.warn("failed connection: " + client);

                    // This is probably the best time to call possible
                    // 'connected' callbacks.
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("ups, something went terribly wrong.", exception);
        }
    }
}
