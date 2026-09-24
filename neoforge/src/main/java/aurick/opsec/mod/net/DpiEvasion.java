package aurick.opsec.mod.net;

import aurick.opsec.mod.config.OpsecConfig;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Best-effort SNI/ClientHello fragmentation for the mod's own outbound HTTPS
 * requests (update check, jar integrity check), aimed at naive DPI middleboxes
 * that only inspect the first TCP segment of a TLS handshake for the plaintext
 * SNI hostname — a pattern reported to affect connectivity in Russia, Iran and
 * China (see e.g. the zapret / GoodbyeDPI / ByeDPI projects, which use the same
 * split-ClientHello idea at the OS/netfilter level).
 *
 * <h2>What this does and doesn't cover</h2>
 * This only wraps requests made through {@link #get}, which uses classic
 * {@link HttpsURLConnection} so a custom {@link SSLSocketFactory} can be
 * installed. It intentionally does NOT touch:
 * <ul>
 *   <li>The Microsoft/Xbox/Minecraft-services login flow in {@code SessionAccount}
 *       — that uses {@code java.net.http.HttpClient}, whose async engine talks to
 *       raw {@link SocketChannel}s via {@code SSLEngine} directly and never goes
 *       through {@link SSLSocketFactory}. There is no public API to fragment its
 *       writes short of reflecting into non-exported JDK internals, which this
 *       mod won't do. If login itself is being blocked, a system-level tool
 *       (zapret, GoodbyeDPI, ByeDPI) alongside the game is the correct fix.</li>
 *   <li>The actual Minecraft game connection — that's a raw TCP socket carrying
 *       the Minecraft protocol, not TLS, so there's no ClientHello to fragment.
 *       A blocked server IP/port needs a system-level bypass tool, not this.</li>
 * </ul>
 * Fragmentation only helps against SNI-inspection DPI, not IP-based blocking —
 * if the destination IP itself is null-routed or a connection is reset by
 * address rather than by SNI, this does nothing.
 */
public final class DpiEvasion {

    private DpiEvasion() {}

    public static boolean isEnabled() {
        return OpsecConfig.getInstance().getSettings().isDpiFragmentTlsHello();
    }

    /** Result of a GET request: HTTP status plus the response body (from the input or error stream). */
    public record Result(int status, String body) {}

    /**
     * Blocking GET via {@link HttpsURLConnection}, with the ClientHello-fragmenting
     * socket factory installed when {@link #isEnabled()}. Callers should run this
     * off the render thread — same contract as the {@code java.net.http.HttpClient}
     * calls it replaces.
     */
    public static Result get(String urlStr, Map<String, String> headers, Duration timeout) throws IOException {
        URL url = URI.create(urlStr).toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        if (isEnabled()) {
            conn.setSSLSocketFactory(FragmentingSSLSocketFactory.INSTANCE);
        }
        conn.setConnectTimeout((int) timeout.toMillis());
        conn.setReadTimeout((int) timeout.toMillis());
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }
        }

        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
        String body = stream != null ? readAll(stream) : "";
        return new Result(status, body);
    }

    private static String readAll(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Wraps the platform default {@link SSLSocketFactory}. For every socket it
     * creates, the underlying plain TCP connection's output stream is swapped for
     * {@link FragmentingOutputStream} before TLS is layered on top, so the very
     * first write on the wire (the ClientHello) is split into two TCP segments.
     */
    private static final class FragmentingSSLSocketFactory extends SSLSocketFactory {
        static final FragmentingSSLSocketFactory INSTANCE = new FragmentingSSLSocketFactory();

        private final SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return layer(new Socket(host, port), host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return layer(new Socket(host, port, localHost, localPort), host, port);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return layer(new Socket(host, port), host.getHostAddress(), port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return layer(new Socket(address, port, localAddress, localPort), address.getHostAddress(), port);
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return delegate.createSocket(new FragmentingSocket(s), host, port, autoClose);
        }

        private Socket layer(Socket raw, String host, int port) throws IOException {
            raw.setTcpNoDelay(true);
            return delegate.createSocket(new FragmentingSocket(raw), host, port, true);
        }
    }

    /**
     * A {@link Socket} that delegates everything to a real, already-connected
     * socket except {@link #getOutputStream()}, which fragments the first write.
     * JSSE's "layered over an existing socket" mode uses exactly this instance
     * as its network conduit, so this is where the ClientHello write lands.
     */
    private static final class FragmentingSocket extends Socket {
        private final Socket raw;
        private FragmentingOutputStream wrappedOut;

        FragmentingSocket(Socket raw) {
            this.raw = raw;
        }

        @Override
        public synchronized OutputStream getOutputStream() throws IOException {
            if (wrappedOut == null) {
                wrappedOut = new FragmentingOutputStream(raw.getOutputStream());
            }
            return wrappedOut;
        }

        @Override public InputStream getInputStream() throws IOException { return raw.getInputStream(); }
        @Override public void connect(SocketAddress endpoint) throws IOException { raw.connect(endpoint); }
        @Override public void connect(SocketAddress endpoint, int timeout) throws IOException { raw.connect(endpoint, timeout); }
        @Override public void bind(SocketAddress bindpoint) throws IOException { raw.bind(bindpoint); }
        @Override public InetAddress getInetAddress() { return raw.getInetAddress(); }
        @Override public InetAddress getLocalAddress() { return raw.getLocalAddress(); }
        @Override public int getPort() { return raw.getPort(); }
        @Override public int getLocalPort() { return raw.getLocalPort(); }
        @Override public SocketAddress getRemoteSocketAddress() { return raw.getRemoteSocketAddress(); }
        @Override public SocketAddress getLocalSocketAddress() { return raw.getLocalSocketAddress(); }
        @Override public SocketChannel getChannel() { return raw.getChannel(); }
        @Override public void setTcpNoDelay(boolean on) throws SocketException { raw.setTcpNoDelay(on); }
        @Override public boolean getTcpNoDelay() throws SocketException { return raw.getTcpNoDelay(); }
        @Override public void setSoLinger(boolean on, int linger) throws SocketException { raw.setSoLinger(on, linger); }
        @Override public int getSoLinger() throws SocketException { return raw.getSoLinger(); }
        @Override public void sendUrgentData(int data) throws IOException { raw.sendUrgentData(data); }
        @Override public void setOOBInline(boolean on) throws SocketException { raw.setOOBInline(on); }
        @Override public boolean getOOBInline() throws SocketException { return raw.getOOBInline(); }
        @Override public void setSoTimeout(int timeout) throws SocketException { raw.setSoTimeout(timeout); }
        @Override public int getSoTimeout() throws SocketException { return raw.getSoTimeout(); }
        @Override public void setSendBufferSize(int size) throws SocketException { raw.setSendBufferSize(size); }
        @Override public int getSendBufferSize() throws SocketException { return raw.getSendBufferSize(); }
        @Override public void setReceiveBufferSize(int size) throws SocketException { raw.setReceiveBufferSize(size); }
        @Override public int getReceiveBufferSize() throws SocketException { return raw.getReceiveBufferSize(); }
        @Override public void setKeepAlive(boolean on) throws SocketException { raw.setKeepAlive(on); }
        @Override public boolean getKeepAlive() throws SocketException { return raw.getKeepAlive(); }
        @Override public void setTrafficClass(int tc) throws SocketException { raw.setTrafficClass(tc); }
        @Override public int getTrafficClass() throws SocketException { return raw.getTrafficClass(); }
        @Override public void setReuseAddress(boolean on) throws SocketException { raw.setReuseAddress(on); }
        @Override public boolean getReuseAddress() throws SocketException { return raw.getReuseAddress(); }
        @Override public void close() throws IOException { raw.close(); }
        @Override public void shutdownInput() throws IOException { raw.shutdownInput(); }
        @Override public void shutdownOutput() throws IOException { raw.shutdownOutput(); }
        @Override public boolean isConnected() { return raw.isConnected(); }
        @Override public boolean isBound() { return raw.isBound(); }
        @Override public boolean isClosed() { return raw.isClosed(); }
        @Override public boolean isInputShutdown() { return raw.isInputShutdown(); }
        @Override public boolean isOutputShutdown() { return raw.isOutputShutdown(); }
    }

    /**
     * Splits the first {@code write} call (the ClientHello) into a small first
     * chunk and the remainder, flushing between the two so they leave as separate
     * TCP segments instead of one. Every write after the first passes through
     * untouched — application data doesn't need this and splitting it would only
     * add latency.
     */
    private static final class FragmentingOutputStream extends FilterOutputStream {
        /** Small enough to guarantee a split even for a short ClientHello, large enough to still contain valid TLS record header bytes in the first segment. */
        private static final int FIRST_CHUNK_BYTES = 4;

        private boolean fragmented = false;

        FragmentingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (fragmented || len <= FIRST_CHUNK_BYTES) {
                out.write(b, off, len);
                return;
            }
            fragmented = true;
            out.write(b, off, FIRST_CHUNK_BYTES);
            out.flush();
            out.write(b, off + FIRST_CHUNK_BYTES, len - FIRST_CHUNK_BYTES);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }
    }
}
