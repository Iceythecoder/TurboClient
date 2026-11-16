# Head healing pools and reward recovery

Head-phase pool duty uses the floor effect `1363` and the incoming projectile
`1355`. RuneLite's generated game values name these `OLM_HEALME_SPOTANIM` and
`OLM_HEALME_SPIT`, respectively:
https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/gameval/SpotanimID.java
Both IDs occur in the inspected Orion attempt. The graphic is treated as a pool
only in head phase; it does not change hand-phase healing detection.

The projectile landing provides an early movement target. Counting starts when
the floor effect appears. The user-defined window is ten game ticks, with a
minimum of eight observed position samples on a pool tile. Repeated client
cycles do not add ticks, missing tick samples are not filled, and clicking a
destination does not count as reaching it. The identified tiles remain the
priority for the ten-tick lifetime even if the visual effect briefly drops out.
After that window, holding continues while the pool remains visible.

Pool movement overrides ordinary dodges and attack requests while entry or a transfer is pending. A settled pool hold permits in-range ranged head attacks with line of sight. Supplies and protection prayers may still run in place. Movement follows collision-valid native paths and normal
visible clicks; game rules and damage are unchanged. Unreachable or late pools
cannot be guaranteed to achieve eight ticks. Saved diagnostics report occupancy,
missed windows and whether the minimum remains possible, plus raw visible pool
tiles in the per-tick frames.

The last inspected post-fight checkpoint still said "Opening reward chest;
waiting for its items." The old code only read legacy `WidgetItem` collections.
Reward scanning now also reads individual dynamic item widgets under the
demonstration's recorded reward component. An unavailable layout is distinct
from an empty chest, and a visible reward window is not repeatedly reopened.

After each Take click, the corresponding inventory quantity must increase.
The empty reward container must be observed on two distinct game ticks before
exit. With a full bag, the bot closes rewards, opens Inventory, drops only an
explicitly configured and live-named Xeric's aid, waits for a free slot, and
reopens the chest. Existing stacks do not require a new slot. Unacknowledged
drops are not repeatedly issued every client cycle.

Window closure uses its visible Close control or ordinary Escape key events.
Off-screen chests and steps trigger viewport zoom or a visible native approach
instead of repeated off-screen interactions. Exit confirmation includes plane
changes, region changes and leaving an instance. The post-raid timeout remains
bounded; failures preserve learning without inventing loot or exit success.

Saved attempts include post-raid stage, pending acknowledgement, inventory-space
state and reward layout diagnostics. No player names or raw chat are added.
Offline tests cover pool timing/priority, missing and duplicate samples, dynamic
reward slots, empty-window confirmation and aid-drop restrictions. Live success
in Orion has not been established by those tests.

## Head damage and health update

Head attacks can use a compatible client-cycle slot after a dodge destination has actually been observed. A requested but unobserved move is never cancelled for a shot. Known later crystal impacts may leave a brief attack slot; directly targeted tiles, imminent or unknown impacts, walls and pending movement keep dodge priority. This interleaves normal input requests and retains observed attack cooldowns; it does not create simultaneous server actions or claim requested attacks dealt damage.

Pool selection prefers a lower hazard score, including incoming crystal landing buffers, among collision-valid routes that preserve the eight-of-ten occupancy budget. A clear occupied pool is held. A transfer is not selected if it would arrive after the final resolution; if all routes miss the minimum, the closest reachable pool is preferred instead of chasing a distant clear tile.

The default absolute healing trigger is now 80 HP (`eatAtHp`), including entry readiness. Healing remains subordinate to required movement and overhead protection, and doses retain their normal confirmation and cooldown rules. This is a healing threshold, not a guarantee HP can never fall below 80. `headAttackRange` defaults to the previous ten-tile ranged baseline and is included in saved profiles. Offline regressions cover stationary pool shots, pending-move suppression, impact preemption, deadline-aware pool choice and HP boundaries; live head damage still needs verification.

The finish flow now continues after the observed exit: a random 1-5 second wait,
Bank chest > Preset Manager > Cox > Load through its visible right-click menu, exact inventory
and equipment quantity comparison with the start-of-run snapshot across two
observed ticks, then a left-click on the outside Chambers entrance. It stops
only after observing the Chambers area transition; no next fight is started.
See OLM-PROFILES-AND-LOOT.md for confirmation and timeout behavior.
