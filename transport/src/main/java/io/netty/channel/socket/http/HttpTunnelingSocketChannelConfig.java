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


import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.socket.SocketChannelConfig;

import javax.net.ssl.SSLContext;

/**
 * The {@link io.netty.channel.ChannelConfig} of a client-side HTTP tunneling
 * {@link io.netty.channel.socket.SocketChannel}.  A {@link io.netty.channel.socket.SocketChannel} created by
 * {@link HttpTunnelingClientSocketChannelFactory} will return an instance of
 * this configuration type for {@link io.netty.channel.socket.SocketChannel#config()}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link SocketChannelConfig},
 * {@link HttpTunnelingSocketChannelConfig} allows the following options in
 * the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "sslContext"}</td><td>{@link #setSslContext(javax.net.ssl.SSLContext)}</td>
 * </tr><tr>
 * <td>{@code "enabledSslCiperSuites"}</td><td>{@link #setEnabledSslCipherSuites(String[])}</td>
 * </tr><tr>
 * <td>{@code "enabledSslProtocols"}</td><td>{@link #setEnabledSslProtocols(String[])}</td>
 * </tr><tr>
 * <td>{@code "enableSslSessionCreation"}</td><td>{@link #setEnableSslSessionCreation(boolean)}</td>
 * </tr>
 * </table>
 * @apiviz.landmark
 */
public final class HttpTunnelingSocketChannelConfig extends DefaultChannelConfig {

    private volatile String serverName;
    private volatile String serverPath = "/netty-tunnel";
    private volatile SSLContext sslContext;
    private volatile String[] enabledSslCipherSuites;
    private volatile String[] enabledSslProtocols;
    private volatile boolean enableSslSessionCreation = true;

    /**
     * Creates a new instance.
     */
    HttpTunnelingSocketChannelConfig(HttpTunnelingClientSocketChannel channel) {
        super(channel);
    }

    /**
     * Returns the host name of the HTTP server.  If {@code null}, the
     * {@code "Host"} header is not sent by the HTTP tunneling client.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Sets the host name of the HTTP server.  If {@code null}, the
     * {@code "Host"} header is not sent by the HTTP tunneling client.
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Returns the path where the {@link HttpTunnelingServlet} is mapped to.
     * The default value is {@code "/netty-tunnel"}.
     */
    public String getServerPath() {
        return serverPath;
    }

    /**
     * Sets the path where the {@link HttpTunnelingServlet} is mapped to.
     * The default value is {@code "/netty-tunnel"}.
     */
    public void setServerPath(String serverPath) {
        if (serverPath == null) {
            throw new NullPointerException("serverPath");
        }
        this.serverPath = serverPath;
    }

    /**
     * Returns the {@link javax.net.ssl.SSLContext} which is used to establish an HTTPS
     * connection.  If {@code null}, a plain-text HTTP connection is established.
     */
    public SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * Sets the {@link javax.net.ssl.SSLContext} which is used to establish an HTTPS connection.
     * If {@code null}, a plain-text HTTP connection is established.
     */
    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Returns the cipher suites enabled for use on an {@link javax.net.ssl.SSLEngine}.
     * If {@code null}, the default value will be used.
     *
     * @see javax.net.ssl.SSLEngine#getEnabledCipherSuites()
     */
    public String[] getEnabledSslCipherSuites() {
        String[] suites = enabledSslCipherSuites;
        if (suites == null) {
            return null;
        } else {
            return suites.clone();
        }
    }

    /**
     * Sets the cipher suites enabled for use on an {@link javax.net.ssl.SSLEngine}.
     * If {@code null}, the default value will be used.
     *
     * @see javax.net.ssl.SSLEngine#setEnabledCipherSuites(String[])
     */
    public void setEnabledSslCipherSuites(String[] suites) {
        if (suites == null) {
            enabledSslCipherSuites = null;
        } else {
            enabledSslCipherSuites = suites.clone();
        }
    }

    /**
     * Returns the protocol versions enabled for use on an {@link javax.net.ssl.SSLEngine}.
     *
     * @see javax.net.ssl.SSLEngine#getEnabledProtocols()
     */
    public String[] getEnabledSslProtocols() {
        String[] protocols = enabledSslProtocols;
        if (protocols == null) {
            return null;
        } else {
            return protocols.clone();
        }
    }

    /**
     * Sets the protocol versions enabled for use on an {@link javax.net.ssl.SSLEngine}.
     *
     * @see javax.net.ssl.SSLEngine#setEnabledProtocols(String[])
     */
    public void setEnabledSslProtocols(String[] protocols) {
        if (protocols == null) {
            enabledSslProtocols = null;
        } else {
            enabledSslProtocols = protocols.clone();
        }
    }

    /**
     * Returns {@code true} if new {@link javax.net.ssl.SSLSession}s may be established by
     * an {@link javax.net.ssl.SSLEngine}.
     *
     * @see javax.net.ssl.SSLEngine#getEnableSessionCreation()
     */
    public boolean isEnableSslSessionCreation() {
        return enableSslSessionCreation;
    }

    /**
     * Sets whether new {@link javax.net.ssl.SSLSession}s may be established by an
     * {@link javax.net.ssl.SSLEngine}.
     *
     * @see javax.net.ssl.SSLEngine#setEnableSessionCreation(boolean)
     */
    public void setEnableSslSessionCreation(boolean flag) {
        enableSslSessionCreation = flag;
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> key, T value) {
        if (channel.config().setOption(key, value)) {
            return true;
        }

        if (HttpTunnelingChannelOption.SERVER_NAME.equals(key)) {
            setServerName(String.valueOf(value));
        } else if (HttpTunnelingChannelOption.SERVER_PATH.equals(key)) {
            setServerPath(String.valueOf(value));
        } else if (HttpTunnelingChannelOption.SSL_CONTEXT.equals(key)) {
            setSslContext((SSLContext) value);
        } else if (HttpTunnelingChannelOption.ENABLED_SSL_CIPHER_SUITES.equals(key)) {
            setEnabledSslCipherSuites((String[])value);
        } else if (HttpTunnelingChannelOption.ENABLED_SSL_PROTOCOLS.equals(key)) {
            setEnabledSslProtocols((String[])value);
        } else if (HttpTunnelingChannelOption.ENABLED_SSL_SESSION_CREATION.equals(key)) {
            setEnableSslSessionCreation((Boolean)value);
        } else {
            return false;
        }

        return true;
    }
}
