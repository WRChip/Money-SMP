Performance audit, 14 September 2026

Audited the MoneySMP Fabric mod in `fabric/` and the Warden mod in `Warden/`, both targeting Minecraft 1.21.11. The older Paper plugin in the root `src/` is a separate implementation and was not changed. Existing Warden edits were preserved; the comparison baseline is the source copied at the start of this audit, not Git HEAD.

| Finding | Impact | Fix |
| --- | --- | --- |
| MoneySMP autosave serialized the entire transaction history and wrote the file on the server thread. | Periodic tick stalls growing with server history; large temporary JSON trees and strings. | Copy a snapshot on the server thread, stream JSON on one background writer, and replace the completed file. Only one regular save may be pending. Shutdown queues and waits for the latest state. |
| MoneySMP transaction commands scanned all history and sent every matching entry at once. | A large query could stall a tick and flood chat packets. | Maintain chronological order, binary-search the start of the requested time window, and show 50 transactions per page. Use `/moneysmp transaction <time> [page]`. All history remains accessible. |
| MoneySMP rebuilt unchanged action-bar text every second. | Repeated number formatting and component allocations per online player. | Reuse text until balance/team changes; retain the existing refresh interval. Parse temporary notices once. |
| Both mods retained notification entries for disconnected players. | Maps could grow with player churn; stale notices could survive reconnects. | Remove entries on disconnect and clear them on server shutdown. |
| Warden scanned inventories with no item limits and scanned ender chests/open containers with no banned items. | Unnecessary recursive work each check interval. | Skip those passes when they have no applicable settings. |
| Warden rebuilt/copy-scanned nested weapon and enchantment contents even when already compliant. | Frequent temporary lists and ItemStack allocations. | Inspect existing contents first and copy only containers that need changes. Weapon checks read item defaults directly and allocate replacement attributes only when needed. Empty weapon settings avoid repeated item-ID string creation. |
| Warden injected the same weapon checks into chained ItemStack constructors. | A normal construction could enforce twice. | Retain the two independent constructor routes and remove redundant wrapper hooks. All five public constructor paths are covered by a regression check. |
| Warden pickup checks ran before vanilla pickup eligibility and rescanned the inventory separately for each restricted item type. | Dropped items and bundles could multiply inventory traversal costs. | Run just before vanilla inventory insertion, after delay/owner checks; count held restricted items in one pass. Immediately reject a pickup whose own contents already exceed a limit. |
| Warden searched every nested stack once for each configured ban. | Work scaled with the number of banned IDs, including IDs not present. | Count relevant contents once, then process only banned IDs actually found. |
| Warden wrote notice preferences on every received update, including unchanged preferences. | Redundant synchronous config writes. | Ignore unchanged preferences, combine changed preferences into at most one write per second, and flush on shutdown. |
| Warden accepted a zero check interval. | Division by zero could stop server ticking. | Normalize to at least one tick and guard the tick calculation. |

The nested-container work also fixed two correctness problems: modifying bundle contents could mutate a shared component, and fixed 27-slot copies could lose slots in larger containers. Updates now operate on copies and preserve the complete slot list. This follows the distinction between reference iteration and copy-producing methods in the [ContainerComponent API](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.4/net/minecraft/component/type/ContainerComponent.html) and [BundleContentsComponent API](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.4/net/minecraft/component/type/BundleContentsComponent.html). The same change keeps Warden's vanilla weapon-stat queries independent of configured overrides.

Measurements used the saved original source and patched source on this Windows machine, with Java 25 and Minecraft/Fabric dependencies from the existing builds. These are synthetic measurements, not live-server TPS or FPS results.

| Workload | Original | Patched |
| --- | --- | --- |
| Calling MoneySMP save with 100,000 transactions | 219.026 ms before returning | 4.240 ms before returning; serialization/disk work continues in the background |
| Warden, 10,000 scans of a shulker containing 27 ordinary damaged swords, empty weapon override map | 68.213 ms; 13,143.8 allocated bytes per scan | 33.412 ms; 112.0 allocated bytes per scan |

That Warden workload allocated approximately 99.1% less memory per scan. Timing varies with JIT compilation, CPU load, and disk conditions. The MoneySMP comparison measures time spent in the caller, not total save completion time.

Both builds pass, including the new `performanceCheck` tasks wired into `check`. Warden checks run through Fabric's server class loader with mixins enabled. They cover constructor routes, weapon caps and default restoration, durability preservation, enchantment overrides, nested bundle/container updates, shared-component isolation, banned-item removal, slots beyond 27, and the zero interval. MoneySMP checks cover a blocked background writer, immutable snapshots, bounded save queuing, shutdown during a pending write, JSON round trips, retained history, and duplicate-timestamp lookup boundaries. Test and benchmark classes are excluded from the release jars.

Validation commands, run from each mod directory:

```powershell
# MoneySMP Fabric
..\Warden\gradlew.bat clean build --offline --console=plain

# Warden
.\gradlew.bat clean build --offline --console=plain
```

The final small Warden optimization was followed by another successful `build`. Logs and the script for recompiling/benchmarking the original source are in [audit-backup-20260914-210451](./audit-backup-20260914-210451/): `moneysmp-build.log`, `warden-build.log`, `moneysmp-baseline.log`, `warden-baseline.log`, and `benchmark.gradle`.

Remaining scaling limits: MoneySMP retains its full history, so startup loading, retained memory, file size, and snapshot copying still grow with that history. Warden still inspects inventories at the configured interval; unusually large nested contents still require traversal. No production server load profile was available, so these changes do not establish an overall TPS/FPS gain or eliminate every possible workload-dependent bottleneck.

Built release jars:

- [MoneySMP-fabric-1.1.1.jar](./fabric/build/libs/MoneySMP-fabric-1.1.1.jar)
- [warden-1.0.4.jar](./Warden/build/libs/warden-1.0.4.jar)

The jars have been built locally. No running server or installed mod directory was changed.
