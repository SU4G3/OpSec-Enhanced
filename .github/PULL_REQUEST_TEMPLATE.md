## What does this change

<!-- Describe the change and why it's needed. Link related issues with "Fixes #123" / "Closes #123". -->

## Build trees touched

<!-- This repo has one Fabric tree (Stonecutter, 12 versions) and 3 independent NeoForge trees
     (neoforge, neoforge-1.21.11, neoforge-26.2). A fix usually needs mirroring to all of them —
     check each one you touched, and verify any you didn't touch genuinely don't need the change
     (NeoForge trees diverge from Fabric in several places: NeoCompat vs FabricLoader, different
     Mixin target descriptors, etc. — don't blind-copy without diffing first). -->

- [ ] Fabric (`src/`, Stonecutter — ran `./gradlew buildAll` or targeted `:<version>:compileClientJava`)
- [ ] `neoforge/` (1.21.1)
- [ ] `neoforge-1.21.11/`
- [ ] `neoforge-26.2/`
- [ ] N/A — change doesn't touch mod code (docs, workflows, templates, etc.)

## Testing

<!-- Compiling is not enough proof for Mixin/version-sensitive changes — describe what you
     actually ran. -->

- [ ] Compiled on the affected tree(s)/version(s)
- [ ] Launched the client (`runClient`) and exercised the changed behavior
- [ ] N/A

## Checklist

- [ ] I read [CONTRIBUTING.md](../CONTRIBUTING.md)
- [ ] No tokens, account data, IPs, or credentials included in this diff or its test fixtures
