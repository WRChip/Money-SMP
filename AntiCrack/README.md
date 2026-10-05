# AntiCrack

Fabric mod for Money SMP, split out of Warden. Stops the world seed being cracked from structure positions and chest loot.

- Minecraft 1.21.11
- Requires [Fabric API](https://modrinth.com/mod/fabric-api)

## Features

- Structure placement (and `/locate`, which reads the same seed) uses the world seed XORed with a one-way hash of it
- Loot table seeds rolled for generated chests are XORed with the same hash

Doesn't apply to superflat worlds: their generator builds the structure placement itself.

## Config

`config/anticrack.json`:

```json
{
  "enabled": true
}
```

Only read at startup. Changing it on an existing world moves structures in every chunk generated afterwards. On first start the setting is copied from the old `anti_seedcrack` section of `warden.json` if there is one.

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`. `build` also runs `e2e` and `e2eMigrate`, which boot a dedicated server and check the scrambling and the config carry-over.
