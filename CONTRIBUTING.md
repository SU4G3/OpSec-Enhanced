# Contributing to OpSec Enhanced

Thanks for taking the time to contribute. This is a passion project maintained in spare time, so please be patient with review turnaround.

## Before you start

- For a **privacy leak** (the mod fails to hide something it claims to hide) or any other security-sensitive bug, do **not** open a public issue or PR that discloses it — use [Security Advisories](https://github.com/SU4G3/OpSec-Enhanced/security/advisories/new) instead. See [SECURITY.md](SECURITY.md).
- For anything else, check [existing issues](https://github.com/SU4G3/OpSec-Enhanced/issues) and [Known Issues](README.md#known-issues) first.
- For a non-trivial feature, open an issue to discuss the approach before writing code — this avoids wasted work if the direction doesn't fit the project.

## Project structure

This repo builds the **same mod** across one Fabric tree and three separate NeoForge trees:

| Tree | Loader | MC versions |
|---|---|---|
| `src/` (root, Stonecutter) | Fabric | 1.20.1 – 26.3 (12 targets) |
| `neoforge/` | NeoForge | 1.21.1 |
| `neoforge-1.21.11/` | NeoForge | 1.21.11 |
| `neoforge-26.2/` | NeoForge | 26.2 |

The Fabric tree uses [Stonecutter](https://stonecutter.kikugie.dev/) to maintain one source set across 12 Minecraft versions via `//? if <condition> { ... //?}` comment blocks. The three NeoForge trees are **independent Gradle projects**, not part of the Stonecutter setup — they frequently diverge from the Fabric tree in non-trivial ways (e.g. `NeoCompat` vs `FabricLoader`, different Mixin target descriptors for NeoForge-remapped method names). See [Building from Source](README.md#building-from-source) for setup and commands.

**When fixing a bug or adding a feature that applies to all loaders:** implement and verify it on one tree first, then mirror it to the others. Before copying a file across trees, diff it against the other tree's copy — several files have real, intentional divergence that a blind overwrite will silently destroy. When in doubt, patch surgically instead of copying the whole file.

**Stonecutter version-conditional blocks (`//? if` comments)** are easy to break in a way the compiler won't catch (an inactive branch inside a comment block isn't compiled against the active version, so bugs there surface only when a *different* version is selected). Test the specific Stonecutter branch your change affects, not just whichever branch happens to be active by default — a successful build on one version does not prove correctness for another.

**Mixin-based changes** should be runtime-verified (`./gradlew :<version>:runClient` or the equivalent in a NeoForge tree), not just compiled — a Mixin can compile cleanly on every target and still fail to apply, or crash, at runtime.

## Making changes

1. Fork the repo and create a branch off `main`.
2. Make your change, following the mirroring and verification guidance above.
3. Run a representative build (`./gradlew buildAll` for Fabric, `./gradlew build` in each NeoForge tree you touched) before opening a PR.
4. Open a PR using the provided template — it includes a checklist for which trees you touched and how you tested.

### Commit messages

This repo loosely follows [Conventional Commits](https://www.conventionalcommits.org/) (`fix:`, `feat:`, `docs:`, `chore:`, etc.), with an issue number in parentheses when the commit addresses one, e.g.:

```
fix: resolve false probe-alert positives on vanilla en_us translation keys (#25)
```

### Code style

Match the surrounding code — this project doesn't enforce a formatter. Avoid adding comments that restate what the code does; comments here are reserved for non-obvious constraints (a version-specific workaround, a subtle invariant, a cross-tree divergence).

## Translations

Language files live at `src/main/resources/assets/opsec/opseclang/<locale>.json` (mirrored to each NeoForge tree). Validate JSON syntax before submitting:

```bash
python3 -c "import json; json.load(open('src/main/resources/assets/opsec/opseclang/<locale>.json'))"
```

## Questions

Open a [Discussion](https://github.com/SU4G3/OpSec-Enhanced/discussions) rather than an issue for open-ended questions.
