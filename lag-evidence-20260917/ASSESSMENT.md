# MoneySMP lag investigation, 17 September 2026

The strongest supported diagnosis is a server tick stall involving chunk lifecycle work. AntiXray remains the leading suspected contributor because removing it was followed by a sustained disappearance of overload warnings. The recovered crash report puts C2ME chunk unloading/status transitions on the server thread and AntiXray obfuscation on a background worker at the watchdog snapshot. This is evidence of the work in progress, not proof of a C2ME bug, a deadlock, or a specific incompatibility.

All remote access was read-only SFTP. Logs, crash reports, selected configuration, and the deployed Warden/C2ME jars were copied here for inspection. No remote settings, mods, worlds, or server state were changed. The SFTP password was entered interactively and was not written to a script or evidence file.

All incident times below are the UTC timestamps in the server logs; add seven hours for Thailand.

## The lag incident

The main incident log is [server/logs/2026-09-16-4.log](server/logs/2026-09-16-4.log). Its compressed bytes are identical to the user's downloaded `2026-09-16-5.log.gz`; remote and downloaded rotation numbers differ.

- The server started at 14:19:53 with AntiXray 1.4.14, C2ME 0.4.0-alpha.0.26, MoneySMP 1.1.5, and Warden 1.0.6-pre.4.
- A single earlier overload warning occurred at 14:21:41.
- Sustained overload began at 16:40:37. Eleven warnings through 16:45:24 reported delays ranging from roughly two seconds to 65.117 seconds.
- At 16:45:19 the watchdog reported a 60-second server tick. Players reported slow eating and unregistered hits, consistent with the recorded server tick failure.
- Join/leave events indicate around 12-14 online players through most of the collapse. The watchdog report lists 11 players at its snapshot.

The recovered [watchdog report](server/crash-reports/crash-2026-09-16_16.45.42-server.txt) contains the missing direct evidence:

| Evidence | Location in report | Interpretation |
| --- | --- | --- |
| Server thread is RUNNABLE in `CompletableFuture.completeExceptionally`, `ItemHolder.setStatus`, `StatusAdvancingScheduler.downgradeStatus0`, and C2ME `ReadFromDisk.preDowngradeFromThis` | Lines 313-341 | The snapshot caught chunk status downgrade/unload callbacks being processed on the server thread. It does not measure how much of the preceding minute each method consumed. |
| Worker-Main-2 is RUNNABLE in AntiXray `layerIntSupplier` and `obfuscate` | Lines 279-292 | AntiXray was actively preparing obfuscated chunk data on a worker. This is not an AntiXray frame on the server thread. |
| Three C2ME workers wait on their work semaphore | Lines 117-144 | They are idle in this snapshot; this does not demonstrate all-core world-generation saturation. |
| C2ME Storage #1 and #2 are in region-file writes and filesystem metadata operations | Lines 451-488 | Chunk persistence was also active. No disk latency measurement is available. |
| Overworld records 1,301 entities, 2,388 block entities, 118,793 scheduled block ticks, and chunk-source counters `23230, 8518` | Line 880 | There was a substantial world/chunk workload. Scheduled block ticks are queued entries, not 118,793 updates necessarily executed in one tick. |

There is no reported out-of-memory exception or demonstrated lock deadlock. The crash memory line records about 3.17 GiB free within 10.59 GiB committed heap, giving about 7.41 GiB used, against a 12 GiB heap maximum. The first number on that Minecraft crash line is free heap, not used heap. Garbage-collection pauses, host CPU throttling, and storage latency cannot be excluded from this SFTP snapshot.

## Why AntiXray is still the first suspect

The next substantial run, [2026-09-16-6.log](server/logs/2026-09-16-6.log), began at 16:46:36 and stopped at 17:37:55. It had no `Can't keep up!` warnings and reached 13 players based on join/leave records.

Comparing its mod list against the incident run, the only removed entries were AntiXray 1.4.14 and its bundled toml4j library. C2ME, VMP, Lithium, MoneySMP, and Warden retained their listed versions. The log explicitly reports the missing AntiXray datapack at line 151. Its compressed bytes match the user's downloaded `2026-09-16-7.log.gz`.

This is a useful before/after comparison with similar player counts. It is not a controlled experiment: the restart cleared transient state, and players' locations and activities changed.

The retained [AntiXray configuration](server/config/antixray.toml) enabled overworld engine mode 3 up to Y=256 with update radius 2; the Nether used mode 1 up to Y=128. These match the documented default example, so the file is not evidence of an accidental extreme custom setting. Mode 3 still performs chunk obfuscation. See the [maintainer's configuration documentation](https://github.com/DrexHD/AntiXray/blob/26.1/README.md).

View distance and simulation distance are both 10 in the collected server properties. Teleports and spectator/creative mode changes appear around the onset. They could increase chunk churn, but these logs do not establish which action triggered the collapse.

## Separate earlier Warden crashes

The reports at [14:06:45](server/crash-reports/crash-2026-09-16_14.06.45-server.txt) and [14:07:11](server/crash-reports/crash-2026-09-16_14.07.11-server.txt) identify a concrete Warden 1.0.6-pre.2 defect: `resolveProjectileWeaponStack` called `isEmpty()` on a null weapon stack while ticking an arrow. Both reports identify the same arrow position across the restart.

That is a confirmed cause of those earlier crashes, separate from the 16:45 watchdog event. The active mod directory now contains Warden 1.0.6-pre.5. Bytecode from the downloaded deployed jar checks for null before calling `isEmpty()`; the relevant method is saved in [warden-null-guard.txt](warden-null-guard.txt). Its SHA-256 is `8736d70e8eb27cac78624e1750d033f2c009be29ca27f6506f876dcd4460f41d`.

The incident log also contains 476 Warden bucket-drain warnings. The last was at 16:35:02, before the sustained 16:40 slowdown. These warnings deserve separate review for behavior/log noise, but they do not establish the cause of the later collapse.

## Profiles and later logs

- The downloaded `RgZG2Q52HE.sparkprofile` retains samples for 22:33:40-23:33:40 UTC on 16 September, after the incident and after AntiXray was removed. The profiler start time is much earlier, but the retained sampling window is only the final hour.
- That hour had 0-3 players. After excluding the empty-server period and its wake-up window, server-thread samples spent about 88.6% in the tick waiting/parking path. The final minute averaged 7.7 ms per tick at 20 TPS. Warden and MoneySMP callbacks were small in that later workload.
- The profile's 0.14 TPS window follows a long empty-server pause. It must not be treated as evidence that the earlier crash was reproduced.
- Spark's activity history contains live links at 13:17, 23:29, and 23:33, with no saved link for the 16:40-16:45 incident. The older 13:17 data endpoint returns 404. The temporary directory contains the current profiler's JFR file, not a preserved crash-time profile.
- The collected 17:38-23:58 log and the following `latest.log` through 01:01:27 contain no overload warnings. This establishes the absence of those warnings, not a guarantee of zero lag.
- C2ME's missing ScalableLux integration-hook warning also appears in healthy later runs, so that startup warning alone does not explain the incident.
- Chunky's three saved tasks all have `cancelled=true`, were last modified at 12:27:32, and `continueOnRestart=false` is configured. The incident log contains no Chunky progress output. There is no evidence here that active background pregeneration was competing with players at 16:40; the saved tasks also do not establish that every area players visited was pregenerated.

## What to do next

1. Keep AntiXray out of the production baseline while checking performance with the usual number of active players. The server already has it removed.
2. If the same stall returns, preserve an all-thread Spark profile from the bad period before restarting, plus the watchdog report. The retained after-hours profile cannot resolve the incident's exact bottleneck.
3. Reintroduce AntiXray or change C2ME only in an isolated, one-change-at-a-time test using comparable player/chunk activity. The snapshot is not enough to justify blaming or removing C2ME outright.
4. Lowering simulation/view distance is a possible temporary way to reduce chunk work, with the tradeoff of a smaller simulated/visible area. No settings were changed during this investigation.

The available evidence supports a chunk-processing failure with AntiXray as the leading suspected contributor. It does not establish a unique offending method or prove a specific AntiXray/C2ME incompatibility.
