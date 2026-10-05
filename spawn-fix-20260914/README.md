Warden spawn/start fix

The supplied [server log](https://mclo.gs/vlvkVF3) records `/warden restore` completing at 14:44:13. Shadow1506 then joins at `(0.5, -63.0, 720.5)` at 14:56:17 and suffocates at 14:56:30. The server was running Minecraft 1.21.11, Warden 1.0.4 and MoneySMP 1.1.1.

Warden set the world default to Adventure during lockdown but never reset that default when the countdown finished. Only online, non-exempt players were changed to Survival. Minecraft 1.21.11 also uses a separate spawn path when the world default is Adventure: it checks a column for collisions without first loading the chunk. Missing terrain is treated as empty, allowing the search to reach the world bottom and return Y -63.

The isolated server test reproduced that exact result without C2ME. With the patch, the same unloaded column resolves to Y -60, the valid surface of the test's four-layer flat world. The test checks both headroom and a floor. This is a reproduction of the mechanism, not an inspection of the production world's block data.

Changes in Warden 1.0.5:

- Restore sets the server default to Survival before releasing the lockdown.
- A per-world persistent state records whether Warden's lockdown has ended. In a restored world with a Survival default, returning non-exempt players with saved Adventure mode are changed to Survival on join.
- Creative, Spectator and the existing operator exemption remain intact. This also means operators in Adventure remain exempt.
- The direct-column spawn path acquires vanilla's spawn-search chunk ticket and checks collisions on the server thread after loading completes.
- Lockdown notices stop after restore, and lifecycle flags reset on shutdown. A recorded lockdown can be reapplied if the server restarts before restore.

Profile inspection: `dZvsLq804e.sparkprofile` covers 14:42:04.905–15:26:05.074 UTC, 52,748 ticks, with 10 ms sampling. All 44 recorded windows report approximately 20 TPS. The final five-minute mean tick time is 18.11 ms; its maximum is 1,354.73 ms. The capture contains occasional stalls but does not indicate sustained low TPS. It has no named spawn frames establishing the bug; the diagnosis comes from the log, Minecraft bytecode and the server reproduction. The profile was decoded locally using spark's protobuf schema.

Verification, from `Warden/`:

```powershell
.\gradlew.bat build spawnRestartCheck --offline --console=plain
```

The checks cover unloaded Adventure spawning, ordinary Survival spawning, the start transition, returning Adventure players, Creative/Spectator/operator exemptions, respawn radius, the PVP grace period, and a second server process loading the saved world. Existing performance checks also run. Test classes are excluded from the release jar. Logs are in `verification.log` and `verification-final.log`.

Install `Warden/build/libs/warden-1.0.5.jar` in place of the old Warden jar and restart the server. No production server was changed by this task. For a world already started under the old version, run `/warden restore 0 "Started" 0` once to set the default and record the restored state. That command also enables PVP immediately; use a nonzero final argument if a grace period is still wanted. Players already stranded underground need a normal teleport to a safe location; the patch fixes future spawn selection.

Existing workspace changes were preserved, including newer unrelated edits made while this task was paused. MoneySMP source was not changed. The `before/` files and `before-sha256.json` record this task's initial source baseline.
