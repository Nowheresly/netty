package io.netty.channel.socket.http;

import io.netty.channel.ChannelOption;
import io.netty.util.internal.PlatformDependent;

import javax.net.ssl.SSLContext;
import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Created by Sylvere Richard on 22/03/15.
 */
public class HttpTunnelingChannelOption<T> extends ChannelOption<T> {

    @SuppressWarnings("rawtypes")
    private static final ConcurrentMap<String, ChannelOption> names = PlatformDependent.newConcurrentHashMap();

    public static final HttpTunnelingChannelOption<String> SERVER_NAME =
            new HttpTunnelingChannelOption<String>("serverName");
    public static final HttpTunnelingChannelOption<String> SERVER_PATH =
            new HttpTunnelingChannelOption<String>("serverPath");
    public static final HttpTunnelingChannelOption<SSLContext> SSL_CONTEXT =
            new HttpTunnelingChannelOption<SSLContext>("sslContext");
    public static final HttpTunnelingChannelOption<String[]> ENABLED_SSL_CIPHER_SUITES =
            new HttpTunnelingChannelOption<String[]>("enabledSslCipherSuites");
    public static final HttpTunnelingChannelOption<String[]> ENABLED_SSL_PROTOCOLS =
            new HttpTunnelingChannelOption<String[]>("enabledSslProtocols");
    public static final HttpTunnelingChannelOption<Boolean> ENABLED_SSL_SESSION_CREATION =
            new HttpTunnelingChannelOption<Boolean>("enableSslSessionCreation");


    protected HttpTunnelingChannelOption(String name) {
        super(name);
    }


    /**
     * Creates a new {@link ChannelOption} with the specified {@param name} or return the already existing
     * {@link ChannelOption} for the given name.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(String name) {
        checkNotNull(name, "name");
        ChannelOption<T> option = names.get(name);
        if (option == null) {
            option = new HttpTunnelingChannelOption<T>(name);
            ChannelOption<T> old = names.putIfAbsent(name, option);
            if (old != null) {
                option = old;
            }
        }
        return option;
    }

}
