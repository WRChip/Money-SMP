# Moderation

Fabric mod for Money SMP, split out of Warden.

- Minecraft 1.21.11
- Requires [Fabric API](https://modrinth.com/mod/fabric-api)

## Commands

All admin only.

- `/freeze <player>`: toggle; the player is held in place and can't break, use, or attack
- `/mute <player>`: toggle; blocks their chat messages
- `/inv <player>`: open their inventory, armour and offhand
- `/enderchest <player>`: open their ender chest

`/inv` and `/enderchest` also work on offline players; edits are written back to their playerdata. Freeze and mute don't survive a restart.

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`. `build` also runs `e2e`, which boots a dedicated server and exercises every command.
