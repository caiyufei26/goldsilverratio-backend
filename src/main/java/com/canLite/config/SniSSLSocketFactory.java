package com.canLite.config;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SNIHostName;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;

/**
 * 在握手前强制设置 SNI（Server Name Indication），用于访问要求 SNI 的 HTTPS 服务（如 GoldAPI）。
 * 当 JVM 全局禁用 SNI（jsse.enableSNIExtension=false）时，通过此 Factory 仍可对指定 host 发送 SNI。
 */
public final class SniSSLSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;

    public SniSSLSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    private static void setSni(SSLSocket s, String host) {
        SSLParameters p = s.getSSLParameters();
        p.setServerNames(Collections.singletonList(new SNIHostName(host)));
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
        SSLSocket socket = (SSLSocket) delegate.createSocket(s, host, port, autoClose);
        setSni(socket, host);
        return socket;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port));
        return createSocket(plain, host, port, true);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
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
