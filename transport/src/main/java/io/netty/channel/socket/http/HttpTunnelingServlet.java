/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.SocketAddress;

/**
 * An {@link javax.servlet.http.HttpServlet} that proxies an incoming data to the actual server
 * and vice versa.  Please refer to the
 * <a href="package-summary.html#package_description">package summary</a> for
 * the detailed usage.
 *
 * @apiviz.landmark
 */
public class HttpTunnelingServlet extends HttpServlet {

    private static final long serialVersionUID = 4259910275899756070L;

    private static final String ENDPOINT = "endpoint";
    private static final String CONNECT_ATTEMPTS = "connectAttempts";
    private static final String RETRY_DELAY = "retryDelay";

    static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelingServlet.class);

    private volatile SocketAddress remoteAddress;
    private volatile EventLoopGroup group = new NioEventLoopGroup();
    private volatile long connectAttempts = 1;
    private volatile long retryDelay;

    @Override
    public void init() throws ServletException {
        ServletConfig config = getServletConfig();
        String endpoint = config.getInitParameter(ENDPOINT);
        if (endpoint == null) {
            throw new ServletException("init-param '" + ENDPOINT + "' must be specified.");
        }

        try {
            remoteAddress = parseEndpoint(endpoint.trim());
        } catch (ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException("Failed to parse an endpoint.", e);
        }

        String temp = config.getInitParameter(CONNECT_ATTEMPTS);
        if (temp != null) {
            try {
                connectAttempts = Long.parseLong(temp);
            } catch (NumberFormatException e) {
                throw new ServletException(
                        "init-param '" + CONNECT_ATTEMPTS + "' is not a valid number. Actual value: " + temp);
            }
            if (connectAttempts < 1) {
                throw new ServletException(
                        "init-param '" + CONNECT_ATTEMPTS + "' must be >= 1. Actual value: " + connectAttempts);
            }
        }

        temp = config.getInitParameter(RETRY_DELAY);
        if (temp != null) {
            try {
                retryDelay = Long.parseLong(temp);
            } catch (NumberFormatException e) {
                throw new ServletException(
                        "init-param '" + RETRY_DELAY + "' is not a valid number. Actual value: " + temp);
            }
            if (retryDelay < 0) {
                throw new ServletException(
                        "init-param '" + RETRY_DELAY + "' must be >= 0. Actual value: " + retryDelay);
            }
        }
    }

    protected SocketAddress parseEndpoint(String endpoint) throws Exception {
        if (endpoint.startsWith("local:")) {
            return new LocalAddress(endpoint.substring(6).trim());
        } else {
            throw new ServletException(
                    "Invalid or unknown endpoint: " + endpoint);
        }
    }

    @Override
    public void destroy() {
        try {
            destroyGroup(group);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to destroy group.", e);
            }
        }
    }

    protected void destroyGroup(EventLoopGroup group) throws Exception {
        group.shutdownGracefully();
    }

    @Override
    protected void service(HttpServletRequest req, final HttpServletResponse res)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(req.getMethod())) {
            if (logger.isWarnEnabled()) {
                logger.warn("Unallowed method: " + req.getMethod());
            }
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        final ServletOutputStream out = res.getOutputStream();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .remoteAddress(remoteAddress)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        final ChannelPipeline pipeline = ch.pipeline();
                        final OutboundConnectionHandler handler = new OutboundConnectionHandler(out);
                        pipeline.addLast("handler", handler);
                    }
                });

        ChannelFuture future = null;
        int tries = 0;
        while (tries < connectAttempts) {

            future = b.connect(remoteAddress).awaitUninterruptibly();
            if (!future.isSuccess()) {
                tries++;
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e) {
                    // ignore
                }
            } else {
                break;
            }
        }

        if (!future.isSuccess()) {
            if (logger.isWarnEnabled()) {
                Throwable cause = future.cause();
                logger.warn("Endpoint unavailable: " + cause.getMessage(), cause);
            }
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        final Channel channel = future.channel();
        ChannelFuture lastWriteFuture = null;
        try {
            res.setStatus(HttpServletResponse.SC_OK);
            res.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/octet-stream");
            res.setHeader(HttpHeaders.Names.CONTENT_TRANSFER_ENCODING, HttpHeaders.Values.BINARY);

            // Initiate chunked encoding by flushing the headers.
            out.flush();

            PushbackInputStream in =
                    new PushbackInputStream(req.getInputStream());
            while (channel.isActive()) {
                ByteBuf buffer;
                try {
                    buffer = read(in);
                } catch (EOFException e) {
                    break;
                }
                if (buffer == null) {
                    break;
                }
                lastWriteFuture = channel.write(buffer);
            }
        } finally {
            if (lastWriteFuture == null) {
                //channel.close();
                future.channel().closeFuture().awaitUninterruptibly();
            } else {
                lastWriteFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private static ByteBuf read(PushbackInputStream in) throws IOException {
        byte[] buf;
        int readBytes;

        int bytesToRead = in.available();
        if (bytesToRead > 0) {
            buf = new byte[bytesToRead];
            readBytes = in.read(buf);
        } else if (bytesToRead == 0) {
            int b = in.read();
            if (b < 0 || in.available() < 0) {
                return null;
            }
            in.unread(b);
            bytesToRead = in.available();
            buf = new byte[bytesToRead];
            readBytes = in.read(buf);
        } else {
            return null;
        }

        assert readBytes > 0;

        ByteBuf buffer;
        if (readBytes == buf.length) {
            buffer = Unpooled.wrappedBuffer(buf);
        } else {
            // A rare case, but it sometimes happen.
            buffer = Unpooled.wrappedBuffer(buf, 0, readBytes);
        }
        return buffer;
    }

    private static final class OutboundConnectionHandler extends ChannelInboundHandlerAdapter {

        private final ServletOutputStream out;

        public OutboundConnectionHandler(ServletOutputStream out) {
            this.out = out;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buffer = (ByteBuf) msg;
            synchronized (this) {
                buffer.readBytes(out, buffer.readableBytes());
                out.flush();
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            if (logger.isWarnEnabled()) {
                logger.warn("Unexpected exception while HTTP tunneling", cause);
            }
            ctx.channel().close();
        }
    }
}
