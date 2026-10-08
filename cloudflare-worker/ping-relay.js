/**
 * OpSec-Enhanced Cloudflare Worker: Minecraft server-list-ping relay (issue #15).
 *
 * Deploy this yourself (see docs/cloudflare-worker-setup.md) and paste the resulting
 * *.workers.dev URL into OpSec's settings (Misc tab -> "Cloudflare Ping Relay URL",
 * with "Lazy Server List Ping" also enabled). Once configured, the multiplayer screen
 * fetches MOTD/player-count/version for every saved server through this worker instead
 * of connecting to each one directly from your own IP -- the worker does the raw TCP
 * handshake on your behalf and hands back only the parsed status JSON.
 *
 * This file is a reference implementation, not a hosted service: nobody but you runs
 * the copy you deploy, so nobody but you (and whichever server you point it at) ever
 * sees which servers you're checking.
 *
 * Usage: GET /ping?host=<server host>&port=<server port, default 25565>
 * Response: {"ok":true,"status":<raw Minecraft server-status JSON>,"latencyMs":<number>}
 *        or {"ok":false,"error":"<message>"}
 */

import { connect } from "cloudflare:sockets";

const HANDSHAKE_TIMEOUT_MS = 5000;

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname !== "/ping") {
      return new Response("Not found", { status: 404 });
    }

    const host = url.searchParams.get("host");
    const portParam = url.searchParams.get("port");
    const port = portParam ? parseInt(portParam, 10) : 25565;

    if (!host || !Number.isInteger(port) || port <= 0 || port > 65535) {
      return json({ ok: false, error: "invalid host/port" }, 400);
    }

    try {
      const started = Date.now();
      const status = await withTimeout(pingServer(host, port), HANDSHAKE_TIMEOUT_MS);
      return json({ ok: true, status, latencyMs: Date.now() - started });
    } catch (e) {
      return json({ ok: false, error: String(e && e.message ? e.message : e) }, 502);
    }
  },
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "access-control-allow-origin": "*" },
  });
}

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error("timed out")), ms)),
  ]);
}

/** Performs the SLP handshake + status request and returns the parsed status JSON object. */
async function pingServer(host, port) {
  const socket = connect({ hostname: host, port });
  const writer = socket.writable.getWriter();
  const reader = socket.readable.getReader();

  try {
    await writer.write(buildHandshakePacket(host, port));
    await writer.write(buildStatusRequestPacket());

    const response = await readStatusResponse(reader);
    return JSON.parse(response);
  } finally {
    try { await writer.close(); } catch {}
    try { await reader.cancel(); } catch {}
  }
}

function buildHandshakePacket(host, port) {
  const hostBytes = new TextEncoder().encode(host);
  const payload = concat(
    varInt(0x00),       // packet id
    varInt(767),        // protocol version (any value -- servers reply with status regardless)
    varInt(hostBytes.length),
    hostBytes,
    uShort(port),
    varInt(1),           // next state: 1 = status
  );
  return concat(varInt(payload.length), payload);
}

function buildStatusRequestPacket() {
  const payload = varInt(0x00); // packet id, no fields
  return concat(varInt(payload.length), payload);
}

/** Reads one length-prefixed packet and returns its JSON string field. */
async function readStatusResponse(reader) {
  const buf = new ByteAccumulator(reader);

  const length = await buf.readVarInt();
  const packetId = await buf.readVarInt(); // consumes toward `length`, expected 0x00
  if (packetId !== 0x00) throw new Error("unexpected packet id in status response: " + packetId);

  const jsonLength = await buf.readVarInt();
  const jsonBytes = await buf.readBytes(jsonLength);
  return new TextDecoder().decode(jsonBytes);
}

/** Pull-based byte accumulator over a ReadableStreamDefaultReader<Uint8Array>. */
class ByteAccumulator {
  constructor(reader) {
    this.reader = reader;
    this.chunks = [];
    this.available = 0;
  }

  async _fill() {
    const { value, done } = await this.reader.read();
    if (done) throw new Error("connection closed before response was complete");
    this.chunks.push(value);
    this.available += value.length;
  }

  async readByte() {
    while (this.available < 1) await this._fill();
    const chunk = this.chunks[0];
    const b = chunk[0];
    if (chunk.length === 1) {
      this.chunks.shift();
    } else {
      this.chunks[0] = chunk.subarray(1);
    }
    this.available -= 1;
    return b;
  }

  async readVarInt() {
    let result = 0;
    let shift = 0;
    while (true) {
      const b = await this.readByte();
      result |= (b & 0x7f) << shift;
      if ((b & 0x80) === 0) break;
      shift += 7;
      if (shift >= 35) throw new Error("VarInt too long");
    }
    return result;
  }

  async readBytes(n) {
    while (this.available < n) await this._fill();
    const out = new Uint8Array(n);
    let offset = 0;
    while (offset < n) {
      const chunk = this.chunks[0];
      const take = Math.min(chunk.length, n - offset);
      out.set(chunk.subarray(0, take), offset);
      offset += take;
      if (take === chunk.length) {
        this.chunks.shift();
      } else {
        this.chunks[0] = chunk.subarray(take);
      }
      this.available -= take;
    }
    return out;
  }
}

function varInt(value) {
  const bytes = [];
  let v = value & 0xffffffff;
  do {
    let b = v & 0x7f;
    v >>>= 7;
    if (v !== 0) b |= 0x80;
    bytes.push(b);
  } while (v !== 0);
  return Uint8Array.from(bytes);
}

function uShort(value) {
  return Uint8Array.of((value >> 8) & 0xff, value & 0xff);
}

function concat(...arrays) {
  const total = arrays.reduce((sum, a) => sum + a.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const a of arrays) {
    out.set(a, offset);
    offset += a.length;
  }
  return out;
}
