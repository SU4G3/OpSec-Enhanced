<p align="center">
<img src="https://i.ibb.co/1thx2zGX/photo-2026-09-19-17-42-43.jpg" alt="opsectransparent" width="15%"/>
</p>
<h1 align="center">OpSec Enhanced</h1>


<p align="center">A client-side Minecraft mod that provides protection against client fingerprinting, tracking exploits, and other privacy focused features.</p>

<p align="center">
<a href="https://modrinth.com/mod/opsec-enhanced"><img alt="Modrinth" src="https://img.shields.io/badge/Modrinth-Under%20Review-00AF5C?logo=modrinth&logoColor=white"></a>
<a href="https://modrinth.com/mod/opsec-enhanced/versions"><img alt="Supported Minecraft Versions" src="https://img.shields.io/badge/Available%20for-1.20%20%E2%80%93%2026.3-00AF5C"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/opsec-enhanced"><img alt="CurseForge Downloads" src="https://cf.way2muchnoise.eu/full_1702991_downloads.svg?badge_style=flat"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/opsec-enhanced"><img alt="Supported Minecraft Versions" src="https://img.shields.io/badge/Available%20for-1.20%20%E2%80%93%2026.3-F16436"></a>
<a href="https://github.com/SU4G3/OpSec-Enhanced/releases/latest"><img alt="GitHub Release" src="https://img.shields.io/github/v/release/SU4G3/OpSec-Enhanced?logo=github&logoColor=white&label=Release&color=6e5494"></a>
</p>

> [!NOTE]
> **OpSec Enhanced** is an independently modified fork of [aurickk/OpSec](https://github.com/aurickk/OpSec) (upstream is no longer actively maintained), published separately at [modrinth.com/mod/opsec-enhanced](https://modrinth.com/mod/opsec-enhanced). It is not affiliated with the original author. The mod checks its own jar's hash against this fork's Modrinth listing on startup (not upstream's) and warns if they don't match — do not treat a jar claiming to be this mod from anywhere else as official.

> [!WARNING]
> This is a passion project built and maintained with **AI**.

## What it does 

- **[Client Spoofer](#client-spoofer)** - Spoof as vanilla (or another known client) and block all mod detections
- **[Channel Spoofing](#channel-spoofing)** - Conditionally block mod network channels to prevent detection
- **[Known-Pack Filtering](#known-pack-filtering)** - Conditionally strip built-in pack identifiers from the configuration handshake
- **[Isolate Pack Cache](#isolate-pack-cache)** - Isolate resource packs per-account to prevent tracking
- **[Block Local URLs](#block-local-urls)** - Block resource pack redirects to local/private addresses
- **[Bypass Server Pack Requirement](#bypass-server-pack-requirement)** - Let the user toggle required server resource pack(s) like client packs 
- **[Strip Mod Shader Overrides](#strip-mod-shader-overrides)** - Strip server resource pack shader overrides targeting non-whitelisted mods
- **[Key Resolution Protection](#key-resolution-protection)** - Protect against key resolution mod detection in any server packet
- **[Meteor Fix](#meteor-fix)** - Disable Meteor Client's flawed key resolution protection
- **[Mod Whitelist](#mod-whitelist)** - Automatically or manually exempt mods from protection
- **[Chat Signing Control](#chat-signing-control)** - Configure chat message signing behavior
- **[Account Manager](#account-manager)** - Switch between Minecraft accounts using session tokens
- **[Telemetry Blocking](#telemetry-blocking)** - Disable data collection sent to Mojang

### OpSec Enhanced additions

- **Encrypted Account Storage** - Saved session/refresh tokens are AES-256-GCM encrypted at rest instead of stored as plaintext, with an owner-only-permissioned key file
- **Skin/Cape Correlation Alerts** - Warns when two saved accounts share the same active skin or cape texture, since that's a real way to link a "main" and an "alt" together
- **Chat Link/Command Guard** - Requires confirmation before a clicked chat/sign/book message copies to your clipboard or runs a command (vanilla does both instantly, unlike `OPEN_URL`), plus an extra heads-up before opening links shaped like phishing/IP-grabber patterns (raw IP, punycode, look-alike characters)
- **Per-Server Pack Cache Isolation** - Extends Isolate Pack Cache to also bucket by server address, closing a side-channel where two colluding servers could share a resource-pack push ID to detect a returning player across servers even without sharing an account
- **Chat Session Key Lockdown** - Skips the Mojang chat-session key-pair fetch entirely when Chat Signing is OFF, instead of only stripping the outgoing per-message signature
- **Sensitive Log Redaction** - Strips token-shaped strings from logged authentication error responses before they hit the log file
- **[Spoof As Options](#spoof-as-options)** - While spoofing as vanilla, optionally advertise as Lunar Client or Badlion Client instead
- **[DPI Evasion (TLS Fragmentation)](#dpi-evasion-tls-fragmentation)** - Splits the mod's own update/integrity-check HTTPS handshake to dodge naive SNI-based connection blocking
- **[Block Cookies](#block-cookies)** - Refuses to store server-set cookies (MC 1.20.5+), which otherwise persist across reconnects and Transfer hops
- **[Client Information Normalizer](#client-information-normalizer)** - Reports a fixed, common language/view-distance/chat-mode/skin-layers/main-hand combo instead of your real settings
- **[Scrub Pack Download Headers](#scrub-pack-download-headers)** - Neutralizes the per-player HTTP headers vanilla sends when downloading a server resource pack
- **[Command History Guard](#command-history-guard)** - Redacts `/login`, `/register`, `/changepassword` etc. arguments before they're written to `command_history.txt`
- **[Auto-Purge Pack Cache On Exit](#auto-purge-pack-cache-on-exit)** - Wipes the downloaded-pack cache on game close, on top of per-account isolation
- **[Lazy Server List Ping](#lazy-server-list-ping)** - Stops the multiplayer screen from auto-pinging every saved server on open
- **Jar Integrity Mirrors** - Integrity check falls back through GitHub Releases and CurseForge if Modrinth doesn't have a match, instead of silently skipping

> If you're interested in servers or plugins that are using tracking related exploits then look in the [Hall of Shame](https://github.com/NikOverflow/ExploitPreventer/blob/master/HALL_OF_SHAME.md).

## Requirements

- **Minecraft** 1.20 – 26.3
- **Fabric Loader** 0.16.0+ (0.18.5+ for MC 26.1.x)
- **Fabric API** (matching your Minecraft version)

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version
2. Download the latest [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version
3. Download the latest `opsec-[minecraft_version]+[version].jar` from [Modrinth](https://modrinth.com/mod/opsec-enhanced) or the [Releases](https://github.com/SU4G3/OpSec-Enhanced/releases/) page
4. Place both mods in your `.minecraft/mods` folder
5. Launch Minecraft

## Configurations

The settings menu is accessible via the `OpSec` button in the multiplayer server selection menu header or via [Mod Menu](https://modrinth.com/mod/modmenu).

<img width="1465" height="820" alt="image" src="https://github.com/user-attachments/assets/c69a768b-60ac-4f78-9705-184f6c4e4495" />


If settings are changed while connected to a server it is recommended to reconnect to the server to ensure changes are applied.

#### Protection Tab

| Setting | Description |
|---------|-------------|
| **Client Spoofer** | Enable/disable [Client Spoofer](#client-spoofer) |
| **Spoof As** | While Client Spoofer is on, choose the advertised brand: Vanilla (default), Lunar Client, or Badlion Client. See [Spoof As Options](#spoof-as-options) |
| **Fragment TLS Handshake** | Enable/disable [DPI Evasion](#dpi-evasion-tls-fragmentation) for the mod's own update/integrity-check requests |
| **Isolate Pack Cache** | Enable/disable [cache isolation](#isolate-pack-cache) |
| **Block Local Pack URLs** | Enable/disable [local URL blocking](#block-local-urls) |
| **Bypass Server Pack Requirement** | Configure [server pack bypass](#bypass-server-pack-requirement) behavior:<br/>• **MANUAL** (default): Default vanilla behavior on push. You can still toggle any server pack.<br/>• **ASK**: Server resource pack not applied but with consent screen to ask if the pack(s) should be applied<br/>• **ALWAYS ON**: Server resource pack not applied by default. You can still toggle any server pack |
| **Strip Mod Shader Overrides** | Enable/disable [shader override stripping](#strip-mod-shader-overrides) |
| **Clear Cache** | Delete all cached server resource packs |
| **Key Resolution Spoofing** | Enable/disable [key resolution protection](#key-resolution-protection) |
| **Fake Default Keybinds** | Return default vanilla keybind values instead of actual bindings |
| **Meteor Fix** | Disable Meteor Client's broken key resolution protection (only shown when Meteor is installed) |
| **Signing Mode** | Configure [chat signing](#chat-signing-control) behavior:<br/>• **OFF**: Strip signatures (maximum privacy)<br/>• **ON**: Default Minecraft behavior<br/>• **AUTO**: Only sign when required (recommended) |
| **Disable Telemetry** | Enable/disable [telemetry blocking](#telemetry-blocking) |
| **Command History Guard** | Enable/disable [command history redaction](#command-history-guard) (default: on) |
| **Block Cookies** | Enable/disable [cookie blocking](#block-cookies) (MC 1.20.5+, default: on) |
| **Client Information Normalizer** | Enable/disable [reporting a fixed common client info combo](#client-information-normalizer) (default: off) |
| **Scrub Pack Download Headers** | Enable/disable [neutralizing per-player pack-download headers](#scrub-pack-download-headers) (MC 1.20.3+, default: on) |
| **Auto-Purge Pack Cache On Exit** | Enable/disable [wiping the pack cache on game close](#auto-purge-pack-cache-on-exit) (default: off) |
| **Lazy Server List Ping** | Enable/disable [skipping the automatic multiplayer-list ping](#lazy-server-list-ping) (default: off) |

#### Whitelist Tab

| Setting | Description |
|---------|-------------|
| **Whitelist Mode** | Select whitelist behavior:<br/>• **BLOCK ALL**: All mod content blocked<br/>• **AUTO**: Mods with network channels are automatically whitelisted (default)<br/>• **CUSTOM**: Manually select which mods to whitelist |
| **Installed Mods** | Toggle individual mods ON/OFF to exempt them from protection (CUSTOM mode only) |

#### Miscellaneous Tab

| Setting | Description |
|---------|-------------|
| **Accent Color** | Cosmetic accent used for the HUD indicator and the multiplayer-screen "OpSec" button |
| **Compact Layout** | Tighter row spacing in this settings menu |
| **Show HUD Indicator** | Small on-screen label showing your current spoof brand. Rendered via a direct Mixin on 1.20.1-1.21.11 (reliable). On 26.1+, uses Fabric API's newer HUD pipeline instead since vanilla's own rendering internals there change shape between patches — **experimental, less tested; report back if it doesn't show up** |
| **Custom Profile For This Server** | Save/clear a settings snapshot tied to the currently-connected server address. Only shown when the config screen is opened with an active connection |
| **Show Alerts** | Display chat messages when tracking is detected |
| **Show Toasts** | Display popup notifications for important events |
| **Log Detections** | Log all detection events to game log for transparency |
| **Debug Alerts** | Show alerts for all probed keys, even unchanged ones |
| **Debug Command** | Enable the `/opsec` debug command. Off by default.

#### Accounts Tab

| Setting | Description |
|---------|-------------|
| **Saved Accounts** | List of added accounts with login/logout and remove buttons |
| **Refresh All** | Revalidate all account tokens (invalid tokens marked red) |
| **Add Session Token** | Add a new account using a session (access) token |
| **Import** | Import accounts from a JSON file |
| **Export** | Export accounts to a JSON file |
| **Randomize Skin** | Uploads a procedurally-generated flat-color skin for the currently logged-in account, via Mojang's official skin API |
| **Randomize Cape** | Picks a random cape from the ones the current account actually owns (or clears it) |
| **Random name button** | On the offline/cracked account screen — fills the username field with a randomly generated name |

### Debug Commands

The `/opsec` command is **off by default** (enable it in Misc → Debug Command). When enabled, use `/opsec` in-game to access debug information:

| Command | Description |
|---------|-------------|
| `/opsec` | Show available commands |
| `/opsec info` | Show overview of all tracked mods |
| `/opsec info <mod>` | Show details for a specific mod (translation keys, keybinds, channels, known packs, shaders) |
| `/opsec channels` | Show all tracked network channels with whitelist status |

### Understanding Alerts

- **Key Resolution Exploit Detected**: Server is probing your keys
- **Resource Pack Fingerprinting Detected**: Suspicious resource pack URL detected
- **Local URL Scan Detected**: Resource pack targeting your local/private address

## Known Issues

- **HUD Indicator on 26.1+ is less battle-tested** than on 1.20.1-1.21.11 (which renders via a direct Mixin). See the [Miscellaneous Tab](#miscellaneous-tab) table above.
- **On 26.3 specifically**, two features are scaled back rather than shipped as an unverified guess, since MC 26.3 changed a lot at once (authlib 9.x→10.x, GLFW→SDL windowing, and a resource-pack composition rework):
  - **Bypass Server Pack Requirement / Strip Mod Shader Overrides don't apply.** 26.3 replaced how server packs are opened internally (`openPrimary`/`openFull` → `openMetadata`/`openResources` returning a `Stream<PackResources>`) — a real architecture change, not a signature tweak, and not something to wrap correctly on a guess without a server actually pushing a pack to test against. Every other protection feature is unaffected.
  - **Account Import/Export uses a fixed file path instead of a native file picker** — 26.3 dropped the `lwjgl-tinyfd` module entirely (window backend moved to SDL). Import reads from, and Export writes to, `opsec-accounts-import.json` / `opsec-accounts-export.json` in your Minecraft config folder.

## Feature Details

### Client Spoofer

Servers can query your client brand to detect whether you're running a modded client. OpSec provides true vanilla spoofing by blocking all mod key resolutions, network channels, and known-pack identifiers (whilst keeping vanilla ones).

- **ON** - Appear as an unmodified Minecraft client
- **OFF** - Appear as a standard Fabric client (default)

Set to OFF by default to allow auto mod whitelisting (whitelist mods with network channels).

---

### Spoof As Options

While Client Spoofer is on, you can pick which brand string OpSec advertises instead of plain `vanilla`:

| Option | Brand sent | Channel behavior | Notes |
|--------|-----------|-------------------|-------|
| **Vanilla** (default) | `vanilla` | Identical to real vanilla | Always accurate, accepted by any server that allows vanilla clients at all |
| **Lunar Client** | `lunarclient:v<version>,fabric` | Identical to vanilla mode (all mod channels blocked) | Lunar's own client uses this vanilla-like network behavior to pass anti-cheat brand checks. OpSec does **not** implement Lunar's proprietary cosmetics protocol behind the `lunarclient:pm` channel some server plugins also check for, so a server doing that specific check may still notice. The version suffix (default `v1.21.11-704,fabric`) is a point-in-time snapshot and will go stale as Lunar ships new builds — edit `lunarVersionSuffix` in `opsec.json` to update it |
| **Badlion Client** | `badlion` | Identical to vanilla mode (all mod channels blocked) | Badlion doesn't register its own plugin channels either, and its brand pattern has no version component, so this option can't go stale the way Lunar's can |

**Why these and not others:** every option here only changes the outbound brand *string* — the underlying channel/known-pack/key-resolution behavior is always identical to plain vanilla mode, because that's what these two clients are documented to actually do on the wire (see [AntiSpoof](https://modrinth.com/plugin/antispoof)'s brand-pattern config and [lunarclient.dev/client-brand](https://lunarclient.dev/client-brand)). Getting this wrong — advertising a brand a server can then contradict via a follow-up check — is worse than not spoofing at all, so OpSec deliberately does not invent channel registrations or protocol responses it can't actually back up. These options only help against a server that denies the generic `vanilla` brand but allowlists specific "known" clients by name; that's an uncommon configuration, so Vanilla remains the safer default for most servers.

---

### DPI Evasion (TLS Fragmentation)

Splits the TLS ClientHello of the mod's own background HTTPS requests (GitHub update check, jar integrity check) across two TCP segments instead of one, so a DPI middlebox that only inspects the first segment for the plaintext SNI hostname doesn't see the full hostname to filter on. This is the same idea used by [zapret](https://github.com/bol-van/zapret), [GoodbyeDPI](https://github.com/ValdikSS/GoodbyeDPI), and [ByeDPI](https://github.com/hufrea/byedpi), which are the actual right tools if the game connection itself (not covered here — see below) is being blocked.

Off by default — enable it in Protection → Fragment TLS Handshake if you're in a region where these background checks are timing out or failing.

**What it covers:** only requests made via OpSec's internal `DpiEvasion` helper — currently the GitHub update check and jar integrity check.

**What it does *not* cover, and why:**
- **Microsoft/Xbox/Minecraft-services login** (`SessionAccount`) — that path uses `java.net.http.HttpClient`, whose async engine talks to raw sockets via `SSLEngine` directly and never goes through a `SSLSocketFactory`, so there's no public API hook to fragment its handshake. If login itself is blocked in your region, run a system-level tool (zapret/GoodbyeDPI/ByeDPI) alongside the game.
- **The actual Minecraft server connection** — that's a raw TCP socket carrying the Minecraft protocol, not TLS. There's no ClientHello to fragment. A blocked server IP/port needs a system-level bypass tool, not a client mod.
- **IP-based blocking in general** — fragmentation only defeats DPI that inspects the SNI field. If the destination IP is null-routed or reset regardless of what's inside the packet, this does nothing.

---

### Block Cookies

MC 1.20.5+ lets a server send `ClientboundStoreCookiePacket` to persist an opaque blob on the client, which the server (or a colluding one) can request back later via `ClientboundCookieRequestPacket` — including across reconnects and `Transfer` hops. That's a ready-made cross-session, cross-server tracker that doesn't depend on your account or IP at all.

OpSec cancels `handleStoreCookie` outright when this is on, so no cookie is ever stored. A subsequent request for it gets vanilla's normal "never set" response (empty payload) — indistinguishable from a client that was never sent one.

On by default (MC 1.20.5+ only; the packet doesn't exist on older versions).

---

### Client Information Normalizer

Every client sends a `ClientInformation` packet on join (language, view distance, chat visibility, chat colors, skin-layer bitmask, main hand, text filtering, server-listing opt-in). That combination of settings is itself a fingerprint — an unusual view distance plus an unusual language plus an unusual skin-layer mask narrows down who you are just as effectively as a mod list would.

When enabled, OpSec replaces the entire outgoing `ClientInformation` with a fixed, common baseline (`en_us`, view distance 10, chat FULL, all skin layers on, main hand right, text filtering off, listing on) instead of whatever you actually have configured. These are vanilla's real fresh-install defaults, not `ClientInformation.createDefault()`'s internal fallback (which reports an unrealistic view distance of 2 and all skin layers off — reporting that would stand out *more* than reporting nothing, same as bad brand spoofing would).

Off by default, since it does mean the server's view of your settings won't match reality (chat visibility mainly — no gameplay-breaking effect either way).

---

### Scrub Pack Download Headers

Decompiling vanilla's `DownloadedPackSource` shows it attaches `X-Minecraft-Username`, `X-Minecraft-UUID`, `X-Minecraft-Version`, `X-Minecraft-Version-ID`, `X-Minecraft-Pack-Format`, and a version-specific `User-Agent` to every server resource pack download request. The pack host sees all of this — including a third-party CDN that isn't the game server itself.

OpSec replaces the strictly-identifying ones (`X-Minecraft-Username`, `X-Minecraft-UUID`, `User-Agent`) with a neutral placeholder before the request goes out. Replacement is by header name, so it's a no-op for any header a given Minecraft version doesn't send. `Version`/`Version-ID`/`Pack-Format` are left alone — they aren't personally identifying on their own, and some pack CDNs may use them to serve the correct pack file, so faking them risks breaking the download for no privacy benefit.

On by default (MC 1.20.3+, when the multi-pack download system was introduced).

---

### Command History Guard

Minecraft's `CommandHistory` persists what you type in chat/commands to `command_history.txt` for arrow-key recall — including `/login <password>`, `/register <password>`, and similar auth-plugin commands, stored in plaintext exactly as typed.

OpSec redacts the arguments of `/login`, `/l`, `/register`, `/reg`, `/changepassword`, `/changepw`, `/cpw`, `/premium`, `/2fa`, and `/authme` before they reach the history list or the file — the command name is kept (so history stays useful) and only the password/argument text is replaced.

On by default (MC 1.20.2+, when `CommandHistory` was introduced — older versions never persisted command history to disk in the first place).

---

### Auto-Purge Pack Cache On Exit

[Isolate Pack Cache](#isolate-pack-cache) buckets downloaded packs per account (and per server), but an isolated cache is still an artifact sitting on disk between sessions.

When enabled, OpSec wipes `downloads/` and `server-resource-packs/` on client shutdown, reusing the same cache-clearing logic as the manual **Clear Cache** button.

Off by default — packs simply re-download next time you connect.

---

### Lazy Server List Ping

Opening the multiplayer screen makes Minecraft automatically ping every saved server to fetch its MOTD/player count/icon — each ping is a real connection attempt (and IP disclosure) to a server you haven't chosen to join yet, just from opening the menu.

OpSec cancels `ServerStatusPinger.pingServer` when this is on, so no automatic pings happen; a saved server shows no preview until you actually connect to it (joining doesn't need the ping data, so this has no effect on connecting).

Off by default.

---

### Isolate Pack Cache
Based on [LiquidBounce](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloadQueue.java).

Server-required resource packs could be used to fingerprint client instance across accounts.

https://alaggydev.github.io/posts/cytooxien/

Instead of storing all resource packs in a shared cache (`~/.minecraft/downloads/`), OpSec creates separate cache directories for each account UUID.

---

### Block Local URLs

Derived from [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) by [NikOverFlow](https://github.com/NikOverflow)

Malicious servers can send resource pack URLs that redirect to your local network to probe for local devices and services.

https://alaggydev.github.io/posts/cytooxien/

OpSec checks if a redirect or normal request targets a local address, then blocks the connection.

---

### Bypass Server Pack Requirement

Servers can push required resource packs the client is forced to apply. Declining them or toggling required server resource pack(s) is impossible on vanilla client. And fake accepting them can be detectable via key resolution probing the client's resource pack key response.

Minecraft still accepts and downloads these packs as normal but OpSec lets you toggle the pack textures at the client level. The language file of the server resource pack is preserved because servers can probe translation keys (e.g. via `{"translate": "some.pack.key"}`) to detect whether the pack is actually applied, and a vanilla client with the pack loaded would resolve those keys to the pack-defined value.

With Opsec installed, server resource pack(s) appears as a normal user-toggleable entry in the resource pack menu so you can flip between stripped and fully-loaded.

**Modes:**
- **MANUAL** (default): Required packs apply fully like vanilla on push. Optional packs follow vanilla toggle semantics. The user can still unequip any server pack from the pack menu to strip it while keeping lang loaded.
- **ASK**: Required packs are stripped on push and a consent overlay prompts `[Continue]` / `[Load Pack For Real]`.
- **ALWAYS ON**: All server packs are stripped on push. No overlay. You can still toggle them back.


---

### Strip Mod Shader Overrides

Some mods (e.g. [Meteor Client](https://github.com/MeteorDevelopment/meteor-client)) render their GUI with their own shaders loaded through Minecraft's resource manager. A forced server resource pack can overide the mod's own files to ship shaders under that mod to either blank the mod's GUI, crash the client with malformed shaders, or GPU DoS, which also fingerprints that the mod is installed.

OpSec strips shader overrides under `assets/<mod>/shaders/` from server packs for any installed mod that isn't whitelisted, so the resource manager falls back to the mod's own bundled shaders. The rest of the pack still loads, so this keeps working even when a server forces the pack to make [Bypass Server Pack Requirement](#bypass-server-pack-requirement) unusable.

Vanilla (`minecraft`) shaders are never touched, and whitelisting a mod lets the server's shader override through.

---

### Key Resolution Protection

Servers can send translatable text containing keys like `key.attack` or `key.hide_icons` in any server packet to probe which keys you have bound or mod UI elements your client can resolve. This can reveal the client's installed mods.

https://wurst.wiki/sign_translation_vulnerability

OpSec tracks when translation keys are being resolved during server packet processing and blocks Minecraft from resolving them based on your selected brand mode:

#### Client Spoofer Behavior

- **ON**: Blocks all mod keys, returns default keybind values for vanilla keys
- **OFF**: Allows Fabric API and whitelisted mod keys, blocks everything else

When **Fake Default Keybinds** is disabled, vanilla keybinds resolve to their actual values.

#### Examples

Spoofing mod keybinds (Returns raw keys/fallback value instead of keybind values):
```
[key.meteor-client.open-commands] '.'→'key.meteor-client.open-commands'
[key.meteor-client.open-gui] 'Right Shift'→'key.meteor-client.open-gui'
```

Spoofing vanilla keybinds with **Fake Default Keybinds** enabled (Returns default keybinds):
```
[key.hotbar.6] 'Q'→'6'
[key.hotbar.7] 'E'→'7'
[key.hotbar.8] 'R'→'8'
```

---

### Meteor Fix

Legacy Meteor client a built-in key protection implementation which can lead to guaranteed detection with the key resolution probing.

The server can use a specially crafted translation key probe with a fallback value, instead of expecting the raw key from a vanilla client, its expecting the fallback value instead. Meteor client echos the raw key back instead of the server probe's fallback value.

When the server uses a sign exploit with fallback value on Meteor Client:
```
'key.meteor-client.open-gui' 'Right Shift'→'key.meteor-client.open-gui'
```

<img width="847" height="107" alt="image" src="https://github.com/user-attachments/assets/e157ae3f-6beb-4823-aca0-9c61573264e2" />

What a Vanilla response would actaully be:
```
'key.meteor-client.open-gui' '⟦FALLBACK⟧'→'⟦FALLBACK⟧'
```
OpSec's bandaid fix for Meteor is to blacklist the `AbstractSignEditScreenMixin` Mixin to disable Meteor's broken key resolution protection. Allowing OpSec's protection to take over, which already handle fallbacks correctly to match the Vanilla response.

<img width="901" height="107" alt="image" src="https://github.com/user-attachments/assets/506b9c73-6747-40f8-9a56-52c0353034b4" />

---

### ExploitPreventer Compatibility

For users that prefers [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer)'s core protection implementation but still need OpSec's additional features, both can be installed alongside each other. Overlapping features are automatically disabled to let EP handle them, note that you would lose OpSec features such as channels spoofing. The following OpSec features are deferred to EP:

- [Brand Spoofing](#brand-spoofing)
- [Channel Spoofing](#channel-spoofing)
- [Known-Pack Filtering](#known-pack-filtering)
- [Isolate Pack Cache](#isolate-pack-cache)
- [Block Local URLs](#block-local-urls)
- [Key Resolution Protection](#key-resolution-protection)
- [Mod Whitelist](#mod-whitelist)

These settings are grayed out in the config screen but your saved preferences are preserved. If you remove EP later, they restore automatically.

Features that don't overlap remain fully functional: alerts, chat signing, account manager, telemetry blocking, [Strip Mod Shader Overrides](#strip-mod-shader-overrides), and [Meteor Fix](#meteor-fix).

---

### Channel Spoofing

Servers can query your registered network channels to detect which mods you have installed.

OpSec can conditionally block mod channels that are registered with the server to prevent detection.
This is enabled by default, its behavior is controlled by the mod whitelist and 

---

### Known-Pack Filtering

Servers can probe your mod-injected pack identifiers that certain mods exposes to detect whether you're running a modded client or using certain mods. 
OpSec intercepts the outgoing `ServerboundSelectKnownPacks` response and strips entries belonging to non-whitelisted mods. Real vanilla and auto whitelisted packs still pass through.

#### Client Spoofer Behavior

- **ON**: Strips all mod-injected packs.
- **OFF**: Keeps packs for whitelisted mods, strips the rest.

> [!NOTE]
> Only active on clients where Fabric's known-packs hook is present (MC 1.21.11+ with modern fabric-api).

---

### Mod Whitelist

Some mods require server communication to function properly (e.g., VoiceChat, Xaero's Minimap quick travel). The whitelist allows you to exempt specific mods from channel spoofing, key resolution protection, known-pack filtering, and shader override stripping.

<img width="853" height="478" alt="whitelist settings menu" src="https://github.com/user-attachments/assets/6ae423de-dd98-47c1-a617-f6df747c9293" />

**Modes:**
- **OFF**: All mod content is blocked
- **AUTO** (default): Mods that register network channels are automatically whitelisted as they are the most likely to have server-side functionalities
- **CUSTOM**: Manually select which mods to whitelist from the installed mod list

When the whitelist is active (AUTO or CUSTOM), [Client Spoofer](#client-spoofer) will be disabled as exposing Fabric mods would need the client brand to match accordingly.

> [!NOTE]
> CUSTOM mode lists every installed mod so any mod can be whitelisted; AUTO mode only shows mods that register network channels.

---

### Chat Signing Control

Based on [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Cryptographic signatures by default are attached to every chat messages. Removing them makes it impossible to track and associate your chat messages with your Minecraft client, and, by extension, Microsoft account.

**Modes:**
- **OFF**: Strip all chat signatures, but prevents you from chatting in servers that enforces secure chat.
- **Auto**: Only sign messages when the server enforces secure chat.
- **ON**: Default Minecraft behavior, signs every messages.

---

### Account Manager

Based on [Meteor Client](https://github.com/MeteorDevelopment/meteor-client).

Add Minecraft accounts with session tokens and switch between them without restarting the game. 

- **Session Token Login** - Add accounts using access tokens 
- **Refresh Token** - Fetch new session tokens for expired accounts
- **Offline Account** - Add username-only accounts without authentication
- **Account Switching** - Click an account to login, click again to logout to original account
- **Token Validation** - Refresh to check if tokens are still valid (expired tokens marked red)
- **Import/Export** - Backup and restore accounts via JSON files

> [!NOTE]
> Session tokens expire after some time. Use the Refresh button to check validity.

---

### Telemetry Blocking

From [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Minecraft collects and sends telemetry data to Mojang, including:
- Game events and player actions
- Performance metrics
- Client configuration
- Usage statistics

OpSec blocks telemetry sending to Mojang when telemetry blocking is enabled. Does not effect gameplay.

---


## Building from Source

### Prerequisites

- **Java 17** (1.20.1 – 1.20.4), **Java 21** (1.20.6 – 1.21.11), **Java 25** (26.1+)
- **Gradle** (included via wrapper)

### Building the Minecraft Mod

1. **Clone the repository**
   ```bash
   git clone https://github.com/SU4G3/OpSec-Enhanced.git
   cd OpSec
   ```

2. **Build all versions**
   ```bash
   # Windows
   .\gradlew.bat build
   
   # Linux/Mac
   ./gradlew build
   ```

3. **Build a specific version**
   ```bash
   # Build for a specific version
   ./gradlew :1.20.1:build
   ./gradlew :1.20.2:build
   ./gradlew :1.20.4:build
   ./gradlew :1.20.6:build
   ./gradlew :1.21.1:build
   ./gradlew :1.21.4:build
   ./gradlew :1.21.6:build
   ./gradlew :1.21.9:build
   ./gradlew :1.21.11:build
   ./gradlew :26.1:build
   ./gradlew :26.2:build
   ./gradlew :26.3:build
   ```

Output JARs are located in `versions/<minecraft_version>/build/libs/`:
| Build Version | Supports |
|---------------|----------|
| 1.20.1 | 1.20 – 1.20.1 |
| 1.20.2 | 1.20.2 |
| 1.20.4 | 1.20.3 – 1.20.4 |
| 1.20.6 | 1.20.5 – 1.20.6 |
| 1.21.1 | 1.21 – 1.21.1 |
| 1.21.4 | 1.21.2 – 1.21.5 |
| 1.21.6 | 1.21.6 – 1.21.8 |
| 1.21.9 | 1.21.9 – 1.21.10 |
| 1.21.11 | 1.21.11 |
| 26.1 | 26.1 – 26.1.2 |
| 26.2 | 26.2 |
| 26.3 | 26.3 |


## References

- [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) - Local URL blocking and server key resolution protection anti-measures
- [LiquidBounce](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/util/MixinDownloadQueue.java) - Cached server resource pack isolation
- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) - Session token sign in
- [No Chat Reports](https://modrinth.com/mod/no-chat-reports) - Chat signing control and telemetry blocking
- [No Prying Eyes](https://github.com/Daxanius/NoPryingEyes?tab=readme-ov-file) - Secure chat enforcement detection
- [MixinSquared](https://github.com/Bawnorton/MixinSquared) - Mixin cancellation for Meteor Fix
- [Stonecutter](https://stonecutter.kikugie.dev/) - Multi-version build system
- [Fabric API](https://github.com/FabricMC/fabric-api) - Fabric translation and keybind keys
- [AntiSpoof](https://modrinth.com/plugin/antispoof) / [lunarclient.dev](https://lunarclient.dev/client-brand) - Client brand pattern sourcing for [Spoof As Options](#spoof-as-options)
- [zapret](https://github.com/bol-van/zapret) / [GoodbyeDPI](https://github.com/ValdikSS/GoodbyeDPI) / [ByeDPI](https://github.com/hufrea/byedpi) - Reference implementations for the TCP-segment-splitting idea behind [DPI Evasion](#dpi-evasion-tls-fragmentation)

## Disclaimer

OpSec is a privacy tool designed to protect players from unwanted client fingerprinting and tracking. It is not intended or encouraged for use in bypassing server rules, evading bans, or gaining unfair advantages. Users are responsible for complying with the rules and terms of service of any server they connect to.
