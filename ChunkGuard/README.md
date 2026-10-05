# ChunkGuard

Fabric mod for Money SMP, split out of Warden. Keeps item and block entity data small enough that it can't push a packet past the client's 2 MB frame limit and disconnect everyone who loads the chunk (a "chunk ban").

- Minecraft 1.21.11
- Requires [Fabric API](https://modrinth.com/mod/fabric-api)

## Features

- Oversized items are removed from player inventories, ender chests and cursors every second (creative players are skipped)
- Oversized items are purged from a container before its contents are sent to whoever opened it
- Oversized dropped items are discarded, can't be picked up, and can't go in item frames
- A chunk whose block entities add up past the cap is sent without them

## Config

`config/chunkguard.json`:

```json
{
  "enabled": true,
  "max_item_bytes": 65536,
  "max_block_entity_bytes": 32768,
  "max_chunk_block_entity_bytes": 1048576
}
```

`max_block_entity_bytes` currently does nothing: its hook is on `BlockEntity.toInitialChunkDataNbt`, which every block entity that sends data (signs, banners, skulls, ...) overrides. The per-chunk total still applies.

Read at startup. On first start the values are copied from the old `chunk_ban` section of `warden.json` if there is one.

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`. `build` also runs `e2e` and `e2eMigrate`, which boot a dedicated server and check each guard and the config carry-over.
