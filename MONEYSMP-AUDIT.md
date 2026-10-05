MoneySMP Fabric correctness audit, 16 September 2026

Audited the MoneySMP Fabric mod in `fabric/` (Minecraft 1.21.11, mod version 1.1.3 at tag `v1.1.3`). The Paper plugin in the root `src/` and the Warden mod were not changed. The zero starting balance for new players was confirmed as deliberate in the team-leaders commit and left alone.

| Finding | Impact | Fix |
| --- | --- | --- |
| An unreadable `data.json` loaded as an empty economy, and only the JSON parse was guarded. | The next autosave replaced the real file with empty data. One bad field (a malformed UUID key, a transaction missing `note`) threw out of the start event instead of being reported. | The whole read is guarded. On failure the mod logs the cause, clears partial state and marks itself read-only, so saving and shutdown never touch the file. Unknown team or tier strings and an out-of-range team count are normalised or dropped with a warning instead of crashing later on join or auction start. |
| Starting an auction (or `/moneysmp post-auction`) zeroed every non-leader's balance. | The pre-check and reserve logic already assumed the team total was available to the leader, so teams could fail to cover minimums and go negative. Each stop-and-restart cycle destroyed the non-leader share of team funds. | Non-leader balances are pooled into the team leader and logged as a transfer. Tiered players without a team are still zeroed. |
| `/moneysmp bid NaN` passed every comparison. | Any player could become top bidder with a NaN bid, corrupting the winner's balance. | Text-parsed amounts must be finite. |
| Moving a team's leader to another team left the old team's leader entry pointing at them. | Nobody on the old team could bid; only the post-auction check noticed. | Team assignment hands leadership to the remaining member with the lowest name. Load validates every leader and repicks stale ones, which heals existing saves. |
| Admin commands could reshape teams and tiers under a live auction. | Sold players could be reassigned, winners could lose their team, leaders could change mid-lot. | `reset`, `randomteams`, `tier`, `team` and `post-auction` are refused while an auction runs. |
| The top bidder could `/pay` away the money behind their bid. | Balance went negative when the lot closed. | Paying is limited to the balance not committed to the current bid. |
| The auction start check compared tier size to the configured team count, not enabled teams. | Auctions started that were guaranteed to stop once unsold players needed placing. | Compare against enabled teams and say so. |
| Renamed players kept their old name mapped to their UUID; self-pay was checked by name. | Payments to the old name went to them. | Old name mapping is removed on join; self-pay is checked by UUID. |
| Removing a player from MoneySMP scoreboard teams removed whatever team they were on. | Other mods' scoreboard teams were cleared for players with no tier or team. | Only `msmp_` teams are touched. |
| Help and usage text said `teamcount <1-9>` and omitted `team leader`. | Wrong guidance; the limit is six. | Text derived from the team list. |

Not changed, worth knowing: `/moneysmp tier set`, `team set` and `team leader` for a player who has never joined still call the Mojang name lookup synchronously on the server thread in online mode. Balances are doubles and displayed floored, so equal splits can leave fractional remainders. Full-randomisation `randomteams` does not enforce one player per tier per team.

Regression checks were added to the existing `performanceCheck` task, which `check` and `build` run: a malformed file must mark the data read-only and never be overwritten, unknown team and tier names and an oversized team count must be normalised, and leadership must hand over when a leader moves. Validation, from `fabric/`:

```powershell
.\gradlew.bat clean build --offline --console=plain
```

Version bumped to 1.1.4. Built jar: [MoneySMP-fabric-1.1.4.jar](./fabric/build/libs/MoneySMP-fabric-1.1.4.jar). No running server or installed mod directory was changed, and nothing was committed.
