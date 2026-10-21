package com.naslink.android;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.*;

/** All HTTPS traffic in this probe must cross the supplied HTTP CONNECT proxy. */
public final class ProxyProbe {
    private ProxyProbe() {}
    public static String line(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int count = 0; count < 8192; count++) {
            int b = input.read();
            if (b == -1) throw new EOFException("连接提前关闭");
            if (b == '\n') return out.toString("US-ASCII").replace("\r", "");
            out.write(b);
        }
        throw new IOException("响应头过长");
    }
    public static int status(String line) throws IOException {
        String[] fields = line.split(" +");
        if (fields.length < 2 || !fields[0].startsWith("HTTP/"))
            throw new IOException("不是有效的 HTTP 响应");
        try { return Integer.parseInt(fields[1]); }
        catch (NumberFormatException ex) { throw new IOException("无效状态码", ex); }
    }
    public static void reachable(String host, int port) throws IOException {
        try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress(host, port), 5000); }
    }
    public static int https(String host, int port) throws IOException {
        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(host, port), 5000);
            raw.setSoTimeout(8000);
            raw.getOutputStream().write(("CONNECT www.google.com:443 HTTP/1.1\r\nHost: www.google.com:443\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            int code = status(line(raw.getInputStream()));
            if (code != 200) throw new IOException("代理 CONNECT 返回 " + code + (code == 407 ? "（需要代理认证）" : ""));
            int total = 0;
            String header;
            do {
                header = line(raw.getInputStream()); total += header.length() + 2;
                if (total > 32768) throw new IOException("代理响应头过长");
            } while (!header.isEmpty());
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket tls = (SSLSocket) factory.createSocket(raw, "www.google.com", 443, true)) {
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(parameters);
                tls.setSoTimeout(8000);
                tls.startHandshake();
                tls.getOutputStream().write(("HEAD /generate_204 HTTP/1.1\r\nHost: www.google.com\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                int result = status(line(tls.getInputStream()));
                if (result != 204) throw new IOException("目标网站返回 HTTP " + result + "，预期 204");
                return result;
            }
        }
    }
}
