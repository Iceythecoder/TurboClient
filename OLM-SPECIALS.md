# Olm test runs, online learning and saved presets

## Start and reuse a run

1. Save any unsaved manual demonstration before restarting OrionNH.exe.
2. Stand in your Olm instance and open Chambers > Start basic test. This uses your second demonstration's gear and baseline setup, and seeds a small neural target model locally from its 120 attack examples. It trusts your assertion of location and looks for a nearby usable entrance when needed. Focus the game canvas for actions.
3. Observe the on-screen Olm status. Unknown mechanics and recoverable input errors keep observation running; Stop/F9 stops outputs. F9 is application-focus scoped, not a system-wide hotkey.
4. After the fight, collect the chest loot yourself. Click Confirm chest looted + save preset and confirm completion. This stops outputs and automatically writes a unique JSON to profiles/learned. Clicking Search on a chest is not treated as proof it was emptied.
5. On another application session, choose Load saved run..., select the JSON, then Run bot. This restores gear, profile, neural weights, movement preferences, explicit target corrections, attack-speed settings and learned acknowledgement timing. Start basic test deliberately starts again from the original candidate setup.

Presets are editable JSON. Advanced settings > Olm baseline displays the current loaded setup, including its checkpoint. Apply Olm profile accepts either a plain profile or learned-preset JSON and validates it before replacing the setup. Edit IDs, potion interval, bounds or gear deliberately; changing neural weights is not necessary for normal setup.

## Entry protocol

Entry is now gate click -> visible Begin the raid option -> observed dialogue dismissal -> second gate click -> observed crossing/encounter. A click alone does not prove entry. Only the exact Begin the raid/Begin raid label is selected; unrelated options are left alone. Existing fights can be recognized from configured actor visibility plus recorded room position. Missing gates, unexpected dialogues and unconfirmed entry remain visible waiting states, while observation and prayer handling continue.

## Faster state and interface processing

The action controller now runs on ClientTick, approximately every 20 ms when the client is healthy, rather than only once per game tick. Expensive hazard scans are spaced three client cycles apart. Health-bar outcome sampling and phase history remain tied to actual game observations. These are client update rates, not guarantees of response time or server action speed.

Inventory, Prayer, Combat and configured Magic actions use visible tabs. Tab opening uses the installed mouse API's primary-button flag; the earlier secondary-button error is corrected. Tab opening and the next interface action occur in separate client cycles. Prayer handling runs first on client/game ticks and projectile events. If a mapped incoming hit requires a protection that is not active, potion and DPS requests yield until the protection is confirmed; dose bookkeeping continues independently. Restoring depleted prayer points is the required exception that enables protection. This prevents inventory work from stealing the interface back. Equipment requests can be issued sequentially while earlier items await server confirmation; pending requests prevent duplicates. A fresh attack request is not repeatedly submitted for the same style in one game tick.

Preparation outside combat is overload, prayer enhance, four aid sips, then revitalisation. During combat the explicit order is protection prayer, recovery to at least 70 actual HP, then offence. Healing can interrupt preparation if the bot is already fighting; only a heal corresponding to a pending preparation aid step advances that step. This does not guarantee HP never drops below 70 after a hit. Preparation advances only after the observed total dose count decreases by exactly one. The shared potionCooldownTicks defaults to three server ticks (about 1.8 seconds), with tick-aligned request spacing and required consumption acknowledgement; earlier cycle-based defaults are no longer used. The usual next-tick inventory update does not add another whole tick of delay, while late acknowledgement defers the schedule. An unconfirmed click waits, then retries without advancing preparation. The selected demonstration confirms aid consumption at ticks 238, 241, 244 and 247, while several intervening clicks consumed nothing. [OSRS potion timing](https://oldschool.runescape.wiki/w/Potions) also documents the three-tick delay. Positive installed COX overload-refresh state avoids taking another overload while it is already active. Dose-family lists are ordered from four doses to one dose.

## Special signals now enabled

| Mechanic | Default signal | Response |
| --- | --- | --- |
| Purple magic orb | Projectile 1341 | Protect from Magic |
| Green ranged orb | Projectile 1343 | Protect from Missiles |
| Red melee orb | Projectile 1345 | Protect from Melee |
| Flame walls | NPC 7558, plus explicitly named flame walls | Approach when necessary, use its actual click action, wait for a real gap, escape and remain outside until despawn |
| Underfoot crystals | Objects 30033/30034; installed Olm crystal graphic | Choose a reachable nonhazardous nearby tile |
| Falling crystals | Projectile/graphic 1357 and configured/installed graphics | Avoid known landing tiles; walk between estimated attack opportunities |

RuneLite's published [spot animation definitions](https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/gameval/SpotanimID.java) identify orb attack styles. Its [NPC definitions](https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/gameval/NpcID.java) identify firewall NPC 7558, which also occurs in the selected demonstration. Installed API constants supply the crystal candidates. These are source-backed defaults, not proof that every Orion variant or impact timing matches. The advanced status shows observed graphic/projectile IDs and active mapping coverage for diagnosing variants.

Explicit mechanics.orbProjectiles overrides the standard orb map. useStandardOrbSignals, useStandardFirewallNpc and useInstalledCrystalSignals can disable the respective defaults. The user confirmed that clicking Orion's wall handles the Lunar action automatically. No guessed Lunar spell selection is used for the default wall interaction. Configured explicit Lunar modes still require a visible Magic tab.

Basic tests allow collision-checked nearby movement on the current plane rather than filtering dodge tiles through the demonstration's narrow movement envelope. Native pathing is used. No coordinates, physics, combat damage, cooldowns or game rules are modified. Lightning, teleport pairing and other unimplemented mechanics are not silently treated as solved.

## What actually learns

- The small target network starts from human demonstration examples, then updates automatically from assessed bot-run state/action/outcome samples. Approximate target HP-bar progress and player HP cost provide a limited reward; confirmed loot and observed death provide small terminal feedback. Missing target health is not rewarded. These are associations and can be confounded by other players, overloads or unavoidable damage.
- Target choices remain constrained by the starting strategy. The network can choose between both low-HP hands inside the final-phase finish rule; it does not override survival or regular-phase mage-first ordering. This is not a full neural controller for every prayer, gear item or Olm mechanic, and no measured improvement is claimed.
- Movement learns from actual arrivals and explicit good/bad feedback. Gear/potion acknowledgement timing learns from actual inventory/equipment changes. Explicit target corrections also update the neural model.
- Bot experiences are marked bot-experience, never inserted into the human demonstration or automatically called correct labels. The run buffer holds at most 1,024 target experiences. The original demonstration file remains unchanged.

## Persistence and privacy

All learning stays in bounded local memory until the user confirms the completed, looted run or explicitly exports movement preferences. The saved JSON includes configuration, item/NPC/projectile IDs, neural arrays, movement/timing scores and bounded numeric bot experiences. It excludes player names, chat, credentials, screenshots and video. No recording or checkpoint is uploaded, automatically cached, or written as a persistent activity log.

Unique file names avoid overwriting earlier runs. Load validates size, schema, gear, movement scores and finite neural matrices; JSON is not executed. Unsaved learning is cleared when its profile is replaced or the application closes. Saved outputs remain until the user removes them. Interrupted final-file writes may leave an invalid output that the loader rejects; ordinary deletion is not guaranteed secure erasure.

## Validation and limits

Automated checks cover orb/fire mappings, underfoot movement, approaching a wall from two tiles away, waiting for a real gap, staying outside, preparation inside the room, preserved pre-arm prayer threats, tab readiness, superseding prayers, acknowledgement handling, same-game-tick reaction, bounded online experience, neural weight updates and checkpoint/preset round trips. UI smoke renders the dashboard and advanced controls.

These checks issue no live game inputs. Live Orion prayer timing, wall clicking, potion effects, movement deadlines, loot collection and combat performance still require an actual run. The app does not control physical mouse-only play throughout, prove equivalence to manual actions, or promise that automation is indistinguishable in server logs.