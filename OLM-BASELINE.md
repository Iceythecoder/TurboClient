# Configured Olm baseline

This is a rule-based client-integrated prototype, separate from demonstration imitation. It has real action paths for potions, a configured room-entry object, gear, NPC attacks and projectile prayers. It is NOT a validated full Olm solver. No gameplay rules or server mechanics are modified. The original saved demonstrations are unchanged.

## Observed evidence

The user selected `orion-chambers-demonstration2.json`. Local analysis found 789 frames, 474 action intentions, and 474 already-learned examples. All recorded phase labels were unknown. No rendered colours were captured.

The recording contains targets 7553 with magic gear, 7555 with melee gear, and 7554 with ranged gear near the end. These roles also agree with the installed client's NPC labels. The observed potion families were cross-checked against that client's ItemID constants. Ordinary projectile IDs 1339/1340 agree with its mage/range labels, but this is not a live validation of Orion prayer resolution timing. The profile under `profiles/olm-demo2-candidate.json` contains these candidates and observed gear. It is explicitly UNVERIFIED, with verification flags false.

There were no recorded entry interactions: the only recorded object actions were searches of object 30028 after combat. That object is not used as an entrance. Entry remains unset. Candidate room bounds are only the observed combat movement envelope (3228..3237, 5731..5747), not proof of the entire room geometry. The observed region 12889 also includes the pre-entry area, so region alone is insufficient.

## Setup

Preserve your current recording before restarting OrionNH.exe. Open Chambers > Advanced settings > Olm baseline. Load observed candidates to inspect the inferred setup, and Use observed gear presets to explicitly copy its items into the main equipment configuration. This overwrites those equipment presets only when you click that button. No raw recording is copied to the project.

Applying a structurally valid profile is allowed before verification; Run bot checks the verification flags. Verify the profile against your inventory and Orion encounter before setting its `verifiedForOrion` flag. Magic attacking uses your weapon's configured normal attack/autocast, not an invented spell selection; `magicAutocastVerified` must reflect actual testing. Adjust the verified room bounds. Set a real entry object/action if needed; with entry unset, Run is permitted only when already inside those room bounds. All requested IDs remain configurable. Head NPC ID alone often cannot identify a phase, so do not label one permanent head ID as HEAD for the whole fight.

Apply Olm profile, then use Run bot on the compact dashboard and focus the game. No actions occur while the game is unfocused or ticks are stale. The initial controller supports normal instance membership checks and only configured nearby NPC targets. It does not assume every instance is Chambers.

## Starting strategy

Preparation requests overload, prayer enhance, four aid sips and revitalisation, with inventory-change acknowledgment before advancing. Emergency aid/restoration takes priority once in the room. A native drink request or changed item ID does not prove a particular stat effect. Missing potions or unusable gear yield a recoverable wait while observation continues.

The configured entry action is requested at most once per eight ticks; entering requires the actual room bounds and nearby configured actors, not a successful click alone. There is no navigation through the rest of Chambers or arbitrary path discovery.

Regular phases target mage, then attempt a configured melee special, then melee. A special is optional when no special preset/energy is available. Final hands reduce melee to a provisional 15% bar threshold, then mage, then finish mage and remaining melee. That is approximate health-bar logic: it does not guarantee avoiding an early kill or meeting Orion's regeneration window. Head uses ranged. Missing/temporarily unavailable targets produce recoverable waits.

## Kill-count phase inference

Observed living-to-dead hand transitions are counted once per tracked actor life. Missing actors, unknown health bars and clicks do not count as deaths. Two confirmed mage deaths AND two confirmed melee deaths imply the next hand cycle is final hands. Three confirmed pairs with no living hands imply head phase. Counters reset for each Run: starting midway through a fight lacks the earlier history and needs a manually supplied phase until enough evidence exists. An explicit head-phase NPC mapping or manual phase can supplement this.

Replay on the user-selected second recording confirmed mage deaths at ticks 353, 519, 826 and melee deaths at 432, 605, 806. Thus the final-hand threshold is reached at tick 605. This is a detection check on that recording, not a live win or proof of combat performance.

The user's green cue belongs to Olm's head/neck. Automatic head/neck colour recognition is NOT implemented; the recording contains no pixels. The existing neck selector supplies a manual label. The Olm baseline tab also has a manual phase fallback including head phase.

## Mistakes, specials and feedback

Normal mapped projectiles select protection prayers. In baseline mode, unmapped projectiles and conflicting threats do not shut down the whole controller; known inputs and observation continue. Unconfirmed prayer requests use a bounded retry delay. This can still miss protection. The standalone strict prayer mode retains its previous stop behavior.

Configured orb prayers, crystal avoidance, flame-wall gap interaction and head-phase walking are implemented; see [OLM-SPECIALS.md](OLM-SPECIALS.md) for calibration and movement learning. The candidate profile leaves special IDs unset. Automatic colour recognition, lightning avoidance and portal pairing are not implemented. Unknown phases retain the last strategy while displaying uncertainty.

Teach target correction stores an explicit context-to-target preference for the last attack decision. Matching later decisions use it. These corrections are limited to target selection, are held in memory and reset when the profile changes. They also teach the small target network and are included in confirmed learned presets; improvement is not established. HP loss is observed but its cause is left unassigned: damage can result from overload, an unavoidable hit, a special, or another cause. The program does not automatically infer that any particular previous action caused it.

The bounded observer continues through recoverable input errors, target waits, action stops and deaths, without writing a trace to disk. Verified-mode outputs stop on leaving the configured area; basic tests trust the user's location assertion. Outputs stop on logout, death, F9 or Stop; those do not erase observation. Closing the main companion clears observer/profile/correction memory. Existing demonstration recording remains separate and blocks bot output; the new observer does not alter the user's saved demonstration or pretend its own actions are human demonstrations.

## Checks

Build checks include OlmStrategyTests for preparation acknowledgments, entry decision, target order, survival priorities, recovery, and deduplicated phase counts. Its optional `--replay-demo` mode verifies the selected recording. UI smoke renders the baseline tab, candidate loading, compact dashboard and floating Stop. No live combat was performed during implementation. Live safety, entry behavior, potion effects, spell/autocast and damage/prayer timings remain unvalidated.
