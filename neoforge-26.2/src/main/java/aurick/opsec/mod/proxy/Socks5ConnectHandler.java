package aurick.opsec.mod.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.IDN;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Minimal SOCKS5 client handshake (RFC 1928, username/password auth per RFC 1929), written
 * from scratch because Minecraft's bundled {@code netty-handler} jar does not include
 * {@code io.netty.handler.proxy.*} (verified: grepped every Netty jar on the runtime
 * classpath, the package is absent — Mojang's build strips it).
 *
 * <p>Added as the very first handler in the pipeline, before Minecraft's own "timeout"/
 * packet-codec handlers. Vanilla's {@code Connection} starts the Minecraft handshake as
 * soon as the channel fires {@code channelActive}; this handler intercepts that raw TCP
 * connection to the PROXY and withholds {@code channelActive} from the rest of the
 * pipeline until the SOCKS5 tunnel to the real destination is actually established, then
 * removes itself so all further bytes pass through untouched. The underlying
 * {@code Bootstrap.connect(...)} target is the proxy's address, not the real server — the
 * real destination only ever appears inside this handshake's own CONNECT request.</p>
 */
public class Socks5ConnectHandler extends ChannelInboundHandlerAdapter {

    private enum State { GREETING, AUTH, CONNECT_REPLY }

    private final InetSocketAddress destination;
    private final String username;
    private final String password;
    private State state = State.GREETING;
    private ByteBuf buffer = Unpooled.EMPTY_BUFFER;

    public Socks5ConnectHandler(InetSocketAddress destination, String username, String password) {
        this.destination = destination;
        this.username = username;
        this.password = password;
    }

    private boolean hasAuth() {
        return username != null && !username.isEmpty();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Do NOT propagate channelActive yet -- Minecraft's own Connection would start
        // sending its handshake packet the instant this fires. Withhold it until the
        // SOCKS5 tunnel to the real destination is actually up.
        ByteBuf greeting = ctx.alloc().buffer(4);
        if (hasAuth()) {
            greeting.writeByte(0x05).writeByte(0x02).writeByte(0x00).writeByte(0x02);
        } else {
            greeting.writeByte(0x05).writeByte(0x01).writeByte(0x00);
        }
        ctx.writeAndFlush(greeting);
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

    private void process(ChannelHandlerContext ctx) throws Exception {
        switch (state) {
            case GREETING -> {
                if (buffer.readableBytes() < 2) return;
                int version = buffer.getUnsignedByte(buffer.readerIndex());
                int method = buffer.getUnsignedByte(buffer.readerIndex() + 1);
                buffer.skipBytes(2);
                if (version != 0x05) throw new IllegalStateException("SOCKS5 proxy sent unexpected version: " + version);
                if (method == 0x02 && hasAuth()) {
                    sendAuth(ctx);
                    state = State.AUTH;
                } else if (method == 0x00) {
                    sendConnectRequest(ctx);
                    state = State.CONNECT_REPLY;
                } else {
                    throw new IllegalStateException("SOCKS5 proxy rejected all offered auth methods (selected 0x"
                        + Integer.toHexString(method) + ")");
                }
                if (buffer.readableBytes() > 0) process(ctx);
            }
            case AUTH -> {
                if (buffer.readableBytes() < 2) return;
                int version = buffer.getUnsignedByte(buffer.readerIndex());
                int status = buffer.getUnsignedByte(buffer.readerIndex() + 1);
                buffer.skipBytes(2);
                if (version != 0x01 || status != 0x00) {
                    throw new IllegalStateException("SOCKS5 proxy authentication failed");
                }
                sendConnectRequest(ctx);
                state = State.CONNECT_REPLY;
                if (buffer.readableBytes() > 0) process(ctx);
            }
            case CONNECT_REPLY -> {
                // VER REP RSV ATYP + bound address (variable) + port(2)
                if (buffer.readableBytes() < 5) return;
                int version = buffer.getUnsignedByte(buffer.readerIndex());
                int reply = buffer.getUnsignedByte(buffer.readerIndex() + 1);
                int atyp = buffer.getUnsignedByte(buffer.readerIndex() + 3);
                int addrLen = switch (atyp) {
                    case 0x01 -> 4;
                    case 0x04 -> 16;
                    case 0x03 -> buffer.readableBytes() < 5 ? -1 : buffer.getUnsignedByte(buffer.readerIndex() + 4) + 1;
                    default -> throw new IllegalStateException("SOCKS5 proxy sent unknown address type: " + atyp);
                };
                if (addrLen < 0) return;
                int total = 4 + addrLen + 2;
                if (buffer.readableBytes() < total) return;
                buffer.skipBytes(total);
                if (version != 0x05) throw new IllegalStateException("SOCKS5 proxy sent unexpected version in CONNECT reply: " + version);
                if (reply != 0x00) {
                    throw new IllegalStateException("SOCKS5 CONNECT failed, proxy returned reply code 0x" + Integer.toHexString(reply));
                }
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
    }

    private void sendAuth(ChannelHandlerContext ctx) {
        byte[] u = username.getBytes(StandardCharsets.UTF_8);
        byte[] p = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
        ByteBuf out = ctx.alloc().buffer(3 + u.length + p.length);
        out.writeByte(0x01).writeByte(u.length).writeBytes(u).writeByte(p.length).writeBytes(p);
        ctx.writeAndFlush(out);
    }

    private void sendConnectRequest(ChannelHandlerContext ctx) {
        String host = destination.getHostString();
        byte[] hostBytes = IDN.toASCII(host).getBytes(StandardCharsets.US_ASCII);
        ByteBuf out = ctx.alloc().buffer(7 + hostBytes.length);
        out.writeByte(0x05).writeByte(0x01).writeByte(0x00); // VER CMD(CONNECT) RSV
        out.writeByte(0x03).writeByte(hostBytes.length).writeBytes(hostBytes); // ATYP=domain
        out.writeShort(destination.getPort());
        ctx.writeAndFlush(out);
    }
}
