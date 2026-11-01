# Money SMP

## Layout
- `src/`, `pom.xml`: Paper plugin (older, separate implementation of the economy).
- `AntiCrack`, `ChunkGuard`, `Moderation`, `Restore`, `riptide-anywhere`: standalone Fabric mods (MC 1.21.11), one Gradle project each.
- `Warden`, `PlayerChains`, `fabric` (MoneySMP Fabric mod): gitlinks with no `.gitmodules`; empty in a fresh clone, not buildable here.
- `MONEYSMP-AUDIT.md`, `PERFORMANCE-AUDIT.md`: audits of `fabric/` and `Warden/`. `audit-backup-*`, `spawn-fix-*`, `lag-evidence-*`: evidence and source snapshots, not build inputs.
- `*-datapack`, `riptide-anywhere-test` (node e2e scripts), `original/MoneySMP.sk`: misc.

## Build (run from the module dir)
- Paper: `mvn -B package` -> `target/MoneySMP.jar` (JDK 21).
- Fabric mod: `./gradlew build --console=plain` in each of AntiCrack, ChunkGuard, Moderation, Restore, riptide-anywhere. Checks run inside `build`; there are no unit tests (`test` is NO-SOURCE).
- CI (`.github/workflows/ci.yml`) runs exactly these.

## Vendored, do not edit
- `grim-src` (gitlink), `lag-evidence-*/jars` and `server/mods` (third-party jars), `**/gradle/wrapper/gradle-wrapper.jar`.

## Server config and roster
- Live server config is not in the repo; `lag-evidence-20260917/server/config` is a snapshot (`warden.json`, c2me, lithium, etc.). Mod config files are `config/<mod>.json` on the server.
- Roster: `Setup-`, `Type-`, `Whitelist-MoneySmpRoster.ps1` at the root (Setup prints commands without `-Apply`; `-Apply` sends over RCON), `moneysmp-setup-commands.txt`, `moneysmp-roster-datapack/`.

## Traps
- First Gradle build downloads Gradle 9.5.1 plus Minecraft and mappings (about 1 minute per mod).
- `gradlew` is committed without the exec bit; use `chmod +x` on Linux.
- Do not put `-Djava.io.tmpdir=C:/temp` back in `gradlew`; it breaks every non-Windows build.
- Audit docs link to jars under `fabric/build` and `Warden/build` that are not in this repo.
- Ignored: `proto-env/`, `__pycache__`, `grim-build*.log`, `build/`, `.gradle/`, `target/`; don't re-add.
- `spawn-fix-*/proto/spark/*_pb2.py` is generated protobuf, not hand-written.
