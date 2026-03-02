package com.goldsilverratio.config;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;

/**
 * 在握手前清空 SNI，避免 Java 8 访问部分 HTTPS 时出现
 * SSLHandshakeException: Received fatal alert: unrecognized_name。
 * 先建普通 Socket 再交给 delegate 并清 SNI，保证握手时已无 SNI。
 */
public final class NoSniSSLSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;

    public NoSniSSLSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    private static void clearSni(SSLSocket s) {
        SSLParameters p = s.getSSLParameters();
        p.setServerNames(Collections.emptyList());
        s.setSSLParameters(p);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        SSLSocket socket = (SSLSocket) delegate.createSocket(s, host, port,
                autoClose);
        clearSni(socket);
        return socket;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port));
        return createSocket(plain, host, port, true);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost,
                               int localPort) throws IOException {
        Socket plain = new Socket(host, port, localHost, localPort);
        return createSocket(plain, host, port, true);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return createSocket(host.getHostName(), port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port,
                               InetAddress localAddress, int localPort)
            throws IOException {
        Socket plain = new Socket(address, port, localAddress, localPort);
        return createSocket(plain, address.getHostName(), port, true);
    }
}
