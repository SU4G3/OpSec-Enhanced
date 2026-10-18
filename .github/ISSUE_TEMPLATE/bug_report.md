---
name: Bug report
about: Report a bug in OpSec Enhanced
title: "(Bug)"
labels: ''
assignees: ''

---

> [!IMPORTANT]
> **Never paste session tokens, refresh tokens, or your `opsec-accounts*.json` files.** Redact usernames, server addresses, IPs and proxy credentials if they are private.
>
> Check the [Known Issues](https://github.com/SU4G3/OpSec-Enhanced#known-issues) section first — e.g. no AUTO whitelist on NeoForge, Bypass Server Pack / Strip Shader Overrides not applying on 26.3, Cloudflare Ping Relay requiring MC 1.20.6+.
>
> If the bug is a **privacy leak** (the mod fails to hide something it claims to hide), please report it privately via a security advisory rather than in a public issue.

**Describe the bug**
A clear and concise description of what the bug is.

**Affected feature**
Which feature is misbehaving? (tick all that apply)
- [ ] Client Spoofer / Spoof As (Lunar / Badlion)
- [ ] Channel Spoofing / Known-Pack Filtering
- [ ] Isolate Pack Cache / Per-Server Isolation / Auto-Purge
- [ ] Block Local URLs / Scrub Pack Download Headers
- [ ] Bypass Server Pack Requirement / Strip Mod Shader Overrides
- [ ] Key Resolution Protection / Meteor Fix
- [ ] Mod Whitelist
- [ ] Chat Signing Control
- [ ] Chat Link/Command Guard / Command History Guard
- [ ] Account Manager (login, import/export, encrypted storage)
- [ ] Telemetry Blocking / Block Cookies / Client Information Normalizer
- [ ] Lazy Server List Ping / Cloudflare Ping Relay
- [ ] Game Connection Proxy / DPI Evasion (TLS Fragmentation)
- [ ] Streamer Mode
- [ ] Settings screen / HUD indicator
- [ ] Other / not sure

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...' (e.g. OpSec settings → Protection tab)
2. Set '...' to '...'
3. Connect to a server / do '...'
4. See error

**Expected behavior**
A clear and concise description of what you expected to happen.

**Actual behavior**
What happened instead?

**Environment**
- OpSec Enhanced version: [e.g. 1.2.3 — see the jar filename]
- Loader: [Fabric / NeoForge] + loader version [e.g. Fabric Loader 0.16.10]
- Minecraft version: [e.g. 1.21.11]
- Fabric API version (Fabric only): [e.g. 0.140.0]
- Java version: [e.g. 21]
- OS: [e.g. Windows 11, Ubuntu 24.04, macOS 15]
- Launcher: [e.g. Prism, official launcher, Modrinth App]
- Jar downloaded from: [Modrinth / CurseForge / GitHub Releases / other]

**Other mods installed**
Mods that may interact with OpSec (e.g. Meteor Client, Mod Menu, BetterF3, No Chat Reports, Exploit Preventer, ViaFabric), or attach the full mod list.

**Server (if relevant)**
- Server software/version: [e.g. Paper 1.21.4, or "singleplayer" / "any server"]
- Game Connection Proxy / Cloudflare Ping Relay enabled? [yes / no]

**Config**
Relevant parts of `opsec.json` (in your `config` folder). **Remove tokens, account data and proxy credentials first.**

<details>
<summary>opsec.json</summary>

```json
paste here
```

</details>

**Logs**
Attach `logs/latest.log` (or a file from `crash-reports/`). For detection-related issues, enable **Log Detections** in the Miscellaneous tab first. Redact tokens and IPs.

**Screenshots**
If applicable, add screenshots (settings screen, error messages, etc.).
