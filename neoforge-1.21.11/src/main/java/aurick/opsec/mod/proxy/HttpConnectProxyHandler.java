package aurick.opsec.mod.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Minimal HTTP CONNECT tunnel handshake, same rationale/pattern as
 * {@link Socks5ConnectHandler} — withholds {@code channelActive} from the rest of the
 * pipeline until the CONNECT response confirms the tunnel is up, then removes itself.
 */
public class HttpConnectProxyHandler extends ChannelInboundHandlerAdapter {

    private final InetSocketAddress destination;
    private final String username;
    private final String password;
    private ByteBuf buffer = Unpooled.EMPTY_BUFFER;

    public HttpConnectProxyHandler(InetSocketAddress destination, String username, String password) {
        this.destination = destination;
        this.username = username;
        this.password = password;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String hostPort = destination.getHostString() + ":" + destination.getPort();
        StringBuilder request = new StringBuilder();
        request.append("CONNECT ").append(hostPort).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(hostPort).append("\r\n");
        if (username != null && !username.isEmpty()) {
            String credentials = username + ":" + (password == null ? "" : password);
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            request.append("Proxy-Authorization: Basic ").append(encoded).append("\r\n");
        }
        request.append("\r\n");
        ByteBuf out = ctx.alloc().buffer();
        out.writeCharSequence(request.toString(), StandardCharsets.US_ASCII);
        ctx.writeAndFlush(out);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf in)) {
            ctx.fireChannelRead(msg);
            return;
        }
        buffer = combine(ctx, buffer, in);
        try {
            process(ctx);
        } catch (Exception e) {
            buffer.release();
            buffer = Unpooled.EMPTY_BUFFER;
            ctx.fireExceptionCaught(e);
            ctx.close();
        }
    }

    private static ByteBuf combine(ChannelHandlerContext ctx, ByteBuf existing, ByteBuf added) {
        if (existing == Unpooled.EMPTY_BUFFER) return added;
        ByteBuf combined = ctx.alloc().buffer(existing.readableBytes() + added.readableBytes());
        combined.writeBytes(existing).writeBytes(added);
        existing.release();
        added.release();
        return combined;
    }

    /** Finds the index of the blank-line header terminator, or -1 if not yet fully received. */
    private static int headerEndIndex(ByteBuf buf) {
        String ascii = buf.toString(buf.readerIndex(), buf.readableBytes(), StandardCharsets.US_ASCII);
        int pos = ascii.indexOf("\r\n\r\n");
        return pos < 0 ? -1 : pos + 4;
    }

    private void process(ChannelHandlerContext ctx) {
        int headerEnd = headerEndIndex(buffer);
        if (headerEnd < 0) {
            if (buffer.readableBytes() > 16384) {
                throw new IllegalStateException("HTTP CONNECT proxy response headers exceeded 16KiB without terminating");
            }
            return;
        }

        String headers = buffer.toString(buffer.readerIndex(), headerEnd, StandardCharsets.US_ASCII);
        String statusLine = headers.split("\r\n", 2)[0];
        int firstSpace = statusLine.indexOf(' ');
        int secondSpace = firstSpace < 0 ? -1 : statusLine.indexOf(' ', firstSpace + 1);
        String statusCode = (firstSpace >= 0 && secondSpace > firstSpace)
            ? statusLine.substring(firstSpace + 1, secondSpace)
            : "";
        if (!statusCode.startsWith("2")) {
            throw new IllegalStateException("HTTP CONNECT proxy refused tunnel: " + statusLine);
        }

        buffer.skipBytes(headerEnd);
        ByteBuf remaining = buffer;
        buffer = Unpooled.EMPTY_BUFFER;
        ctx.pipeline().remove(this);
        ctx.fireChannelActive();
        if (remaining.isReadable()) {
            ctx.fireChannelRead(remaining);
        } else {
            remaining.release();
        }
    }
}
