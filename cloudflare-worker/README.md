# OpSec Ping Relay — Cloudflare Worker setup

Resolves [issue #15](https://github.com/SU4G3/OpSec-Enhanced/issues/15): lets the
multiplayer screen show live MOTD/player-count/version for every saved server
*without* connecting to each one directly from your own IP. The worker opens the raw
TCP connection on your behalf (using Cloudflare's [TCP Sockets
API](https://developers.cloudflare.com/workers/runtime-apis/tcp-sockets/), available on
the free plan) and hands the mod back only the parsed status JSON.

This is entirely optional and requires you to deploy your own copy — nobody but you
runs it, so nobody but you (and whichever server you point it at) ever sees which
servers you're checking.

## Deploy

1. Install [Node.js](https://nodejs.org/) if you don't have it, then install Wrangler
   (Cloudflare's CLI):
   ```
   npm install -g wrangler
   ```
2. Log in (opens a browser to authorize against your free Cloudflare account):
   ```
   wrangler login
   ```
3. From this directory (`cloudflare-worker/`), deploy:
   ```
   wrangler deploy
   ```
   Wrangler prints a URL like `https://opsec-ping-relay.<your-subdomain>.workers.dev`
   when it finishes. That's your relay URL.

## Configure the mod

1. Open OpSec's settings → **Misc** tab.
2. Turn on **Lazy Server List Ping** (this is what stops the mod from auto-pinging
   every saved server directly in the first place).
3. Turn on **Cloudflare Ping Relay**.
4. Paste your worker URL (the full `https://...workers.dev` address, no `/ping` suffix
   needed — the mod appends that itself) into **Cloudflare Ping Relay URL**.

Saved servers will now show live status again, routed through your worker.

## Limitations

- Requires Minecraft **1.20.6 or newer** (older versions' `ServerStatusPinger` has a
  different method shape this mod doesn't special-case for — pre-1.20.6, Lazy Server
  List Ping still works, it just can't be backed by the relay).
- The player-list hover tooltip isn't populated (MOTD, player count, and version still
  show correctly) — this trims a bit of per-version formatting complexity vanilla
  normally handles internally.
- Cloudflare's free plan limits a Worker invocation to 10ms of CPU time and a handful
  of simultaneous TCP sockets; this is normally plenty for one-off status pings but
  can be a bottleneck if you have a very large saved-server list and refresh them all
  at once.
