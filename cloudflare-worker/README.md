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

### Option A: from inside the mod (recommended, no CLI needed)

1. Create a free [Cloudflare account](https://dash.cloudflare.com/sign-up) if you don't have one.
2. Get an API token: dash.cloudflare.com → your profile icon (top right) → **My Profile** → **API Tokens** → **Create Token** → use the **Edit Cloudflare Workers** template → under "Account Resources" select your account → **Continue to summary** → **Create Token** → copy it (shown once).
3. Get your Account ID: it's the first path segment after `dash.cloudflare.com/` in your browser's address bar while on the dashboard (a long hex string).
4. In OpSec's settings → Protection tab → turn on **Lazy Server List Ping** → turn on **Cloudflare Ping Relay** → **Configure Ping Relay...** → paste the API token and Account ID under "Auto-deploy" → **Deploy Worker**.

The mod uploads the worker, enables its `workers.dev` route, and fills in the URL for you. Neither the token nor the account ID is saved — only the resulting worker URL is.

### Option B: manually, with Wrangler

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
   when it finishes. In OpSec's settings → Protection tab → turn on **Lazy Server List
   Ping** → turn on **Cloudflare Ping Relay** → **Configure Ping Relay...** → paste that
   URL into the **Worker URL** field at the bottom → **Save**.

Either way, saved servers will now show live status again, routed through your worker.

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
- **Large, security-hardened servers (e.g. Hypixel, 2b2t) commonly firewall off
  datacenter/cloud IP ranges, including Cloudflare's** — pinging one of these through
  the relay will fail with `"proxy request failed, cannot connect to the specified
  address"` even though the relay itself is working correctly (verified by connecting
  to other non-Minecraft hosts/ports from the same worker). This is the target
  server's own anti-bot filtering, not a bug in the relay. Smaller/community servers
  that don't block cloud ranges work fine.
- **Auto-deploy (Option A) needs a `workers.dev` subdomain already registered on the
  account** — a brand-new Cloudflare account doesn't have one yet. If deploy fails with
  a message about that, go to dash.cloudflare.com → Workers & Pages and set one up
  (one-time, free, just picks a name like `<you>.workers.dev`), then try again.
