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

package io.bitbang.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

/**
 * http://thushw.blogspot.co.at/2011/06/asynchronous-udp-server-using-java-nio.html
 */
public class AsyncUdpServer {
    class Connection {
        ByteBuffer request = ByteBuffer.allocate(1024);
        ByteBuffer response;
        SocketAddress address;
    }

    static int port = 8340;

    private void process() {
        try {
            Selector selector = Selector.open();
            DatagramChannel channel = DatagramChannel.open();
            channel.socket().bind(new InetSocketAddress(port));
            channel.configureBlocking(false);

            channel.register(selector, SelectionKey.OP_READ, new Connection());

            while (selector.isOpen()) {
                try {
                    selector.select();
                    Iterator selectedKeys = selector.selectedKeys().iterator();
                    while (selectedKeys.hasNext()) {
                        try {
                            SelectionKey key = (SelectionKey) selectedKeys.next();
                            selectedKeys.remove();
                            if (!key.isValid()) continue;

                            if (key.isReadable()) read(key);
                            else if (key.isWritable()) write(key);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void read(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Connection connection = (Connection) key.attachment();
        connection.address = channel.receive(connection.request);
        System.out.println(new String(connection.request.array(), "UTF-8"));
        connection.response = ByteBuffer.wrap("send the same string".getBytes());
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void write(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Connection connection = (Connection) key.attachment();
        channel.send(connection.response, connection.address);
        key.interestOps(SelectionKey.OP_READ);
    }

    static public void main(String[] args) {
        new AsyncUdpServer().process();
    }
}
