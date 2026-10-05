# Restore

Fabric mod for Money SMP, split out of Warden. Locks a freshly generated world down until an admin opens it.

- Minecraft 1.21.11
- Requires [Fabric API](https://modrinth.com/mod/fabric-api)

## Features

- The first time the overworld is generated it is locked down: adventure mode, PvP off, respawn radius 0, a 50 block border around spawn, and ticking frozen. The first player to join is told why.
- `/restore` runs a countdown, lifts the lockdown, shows a title, then counts down a PvP grace period with reminders at 1/2, 1/4, ... of the time left
- Players who were offline during the restore and saved in adventure mode are moved to survival when they join
- Spawn locating loads the spawn column first during the lockdown, so players don't spawn on bedrock in an unloaded chunk

The lock state is still saved as `data/warden_world.dat`, so a world locked or restored under Warden keeps its state.

## Commands

- `/restore <countdownSeconds> <"endTitle"> <pvpDelayMinutes>` (admin)

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`. `build` also runs `e2e` and `e2eRestart`, which boot a dedicated server and check the lockdown, restore and restart behaviour.
