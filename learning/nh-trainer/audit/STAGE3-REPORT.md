Current agent uses [native potion boosting and brew-aware restoration/reboosting](BOOST-POLICY.md), with fixed game rules.

Current rules: [restored fixed game mechanics and verified parity](RULES-RESTORATION.md). Earlier Stage 3 results below are historical and ineligible for normal-game training.

Current viewer uses fighter 1.0.2: [DD position confirmation, repeated cooldown DD and rendered overlap validation](DD-POSITION-FIX.md).

Current viewer uses fighter 1.0.1 with the [DD approach correction and fresh baseline](DD-APPROACH-FIX.md). The report below records the preserved original 1.0.0 baseline.

# Stage 3 — deterministic NH starter fighter

Status: acceptance gate passed on 2026-10-14. Stage 3 was explicitly approved by the user.
The user's follow-up clarified DD behavior: freeze first, then DD during cooldown; an attack click routes out without another walk-away input.

## Delivered code

`scripted_fighter.py` composes TargetManager, StateTracker, TickManager, GearManager, PrayerManager, SpellManager, AttackManager, SupplyManager, DecisionEngine, ActionScheduler and TelemetryManager, with source-backed evidence and a movement planner.

- TargetManager locks the target ID, controller side and episode generation; StateTracker rejects backwards ticks.
- TickManager measures decisions against a 600 ms budget. The simulator advances logical ticks without real-time sleeping. No claim is made about live network/tick deadlines.
- GearManager prepares gear only near attack readiness and avoids requesting an already equipped setup. Native equipment swapping changes only differing slots and conserves actual starter inventory.
- PrayerManager selects visible defensive protection through approved rules. The native controller applies compatible offensive prayer for the selected style when protection/prayer points permit.
- SpellManager checks Ice Barrage's level requirement. The existing native gear/attack controller selects its autocast spell; no invented spell action channel is used.
- AttackManager chooses attacks and approved AGS opportunities. ActionScheduler checks controller side, generation, tick and rule hash before execution.
- SupplyManager uses native `canConsume` eligibility, including food/potion cross-locks, stock and legal partial-combo fallbacks.
- DecisionEngine keeps supply priority and refuses contradictory supply-plus-attack actions. Explicit movement is not combined with an attack.
- TelemetryManager records accepted/rejected native operations in memory. Accepted attack intent is distinguished from confirmed attack events.

`browser-source/nh-starter/worker-stage3.ts/.mjs` is a separate bridge using the pinned public combat APIs. It adds read-only native estimates, cache-checked movement candidates, precise consumable eligibility, paired mirror decisions, and stacked-target attack routing through native tile primitives.

`nh_starter_env.py` accepts only the explicitly supported Stage 3 worker override. Its existing default worker remains unchanged. Stage 1 tracing is rejected for the new bridge until a separately versioned telemetry integration is implemented.

`audit/stage3.py` runs the fixed baseline and archives results/source exclusively to a new output directory.
`audit/test_stage3.py` contains 18 acceptance tests.
`audit/verify_stage3.py` verifies archive integrity and optionally exactly reproduces a chosen fight.

## Freeze and DD behavior

Deliberate DD requires an actually frozen opponent, enough remaining freeze time, at least two cooldown ticks, available movement, and a verified one-tile path underneath. It is attempted at most once per observed freeze and is suppressed during supply use.

While under the opponent, the policy produces no additional walk-away action. When its attack is ready, it submits the chosen attack with movement NONE. The Stage 3 adapter handles the automatic one-tile step-out through collision-checked native state updates before combat execution. Frozen actors cannot use that movement. A regression test verifies the attack-only input, resulting step-out and confirmed attack.

The pinned native combat engine already exposes movement requirements for attacks, but the previous bridge returned no route when distance was zero. This was the source of a mirror deadlock. The adapter correction is local simulator behavior matching the user's requested attack-click semantics; it does not establish Orion parity.

## Final 50-fight baseline

Selected archive: `build/stage3-baselines/2026-10-14-attack-dd-baseline`.
Seeds: 9300–9349. Maximum: 256 ticks per fight. Opponents cycle through six policies.

| Opponent | Fights | Wins | Draws |
| --- | ---: | ---: | ---: |
| Stationary ranged | 9 | 6 | 3 |
| Fixed Magic | 9 | 9 | 0 |
| Fixed melee | 8 | 6 | 2 |
| Seeded rapid switching | 8 | 5 | 3 |
| Seeded random prayer | 8 | 6 | 2 |
| Same fighter mirror | 8 | 0 | 8 |
| Total | 50 | 32 | 18 |

The mirror uses a second instance of the same fighter on the same pre-action state, with explicit controller-side/physical-actor mapping. Both vectors are submitted together before native combat resolution. Stationary ranged and fixed ranged share a ranged attack policy; random-prayer opponents randomize their protection deterministically from seed and tick.

- 11,535 logical ticks; 2,140 confirmed learner-side attacks.
- Damage dealt: 19,574; received: 10,552.
- Learner-side preflight and execution rejections: **0**.
- Decision deadline misses: **0**. Maximum measured learner decision time: approximately **5.81 ms** on this computer.
- Zero-healing supply uses: **0**. This metric detects wholly wasted consumption, not every partial-heal opportunity cost.
- Longest gap between confirmed learner attacks: **12 ticks**, below the 64-tick stall gate.
- Deliberate DD occurred 33 times across the melee and rapid-switch cases.
- All draws reached the configured time limit. They are not counted as wins.

Reliability gate: zero preflight/execution rejections, zero missed deadlines, zero wholly wasted supply uses, and no attack gap exceeding 64 ticks. This is a starting reliability benchmark, not a combat-strength promotion test.

## Tests and reproducibility

18 Stage 3 tests passed, including real AGS execution with legal shield-stow inventory space, actual supply consumption, resource/cooldown/freeze checks, deterministic repeat fights, mirror-side routing, target stability, DD gating and attack-only exit, starter gear restrictions, and the previously stalled mirror seed.

All 18 Stage 2 and 9 Stage 1 regression tests passed. The separate Stage 3 TypeScript project type-check and esbuild bundle build passed.

The archive's 12 integrity entries and matching source snapshots were verified. Seed 9317 was rerun and its complete decision/observation/event digest exactly matched the saved baseline.

From the project root:

```powershell
$env:PYTHONPATH=(Resolve-Path learning\nh-trainer).Path
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\test_stage3.py
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\test_stage2.py
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\test_stage1.py
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\verify_stage3.py build\stage3-baselines\2026-10-14-attack-dd-baseline --replay-seed 9317
# Choose a NEW directory; existing baselines are never overwritten.
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\stage3.py --output build\stage3-baselines\my-new-run
```

Build/type-check from `learning/nh-trainer/browser-source`:

```powershell
& '..\..\tools\node-v22.22.0-win-x64\node.exe' build-stage3.mjs
& '..\..\tools\node-v22.22.0-win-x64\node.exe' node_modules\typescript\bin\tsc -p nh-starter\tsconfig-stage3.json
```

The archive contains configuration, code, compiled worker, provenance and result hashes, with source copied before evaluation. It is read-only and treated as immutable; hashes detect changes rather than guaranteeing tamper prevention. Reproduction requires the matching existing Python/Node dependencies and game fixtures. The snapshot is not a standalone application installer.

## Bugs found and fixed

- Approximate supply masks missed the native food/potion cross-lock, causing rejected potion/food attempts. Source-backed `canConsume` filtering and partial-combo selection corrected learner output.
- Gear preparation during every cooldown tick caused excessive switching. Preparation is now limited to the final cooldown tick, with no-op suppression.
- Movement definitions included a legacy `pressure` entry absent from the current action vector. Candidates are restricted to the actual descriptor.
- Native attack events identify the attacker via `attackerId`; initial baseline counting was corrected.
- The extended opponent schedule initially retained a four-opponent modulo. A coverage test now verifies all six policies occur in the 50 cases.
- One mirror seed produced 217 ticks without a confirmed attack despite many accepted intents. Stacked-target routing now produces an automatic step-out from attack intent; the same seed passes the stall test.
- The AGS test initially omitted full-inventory shield stowing and food delay. It now consumes a real food item to free space, waits the native delay, and verifies actual special energy debit and attack events.

Earlier diagnostic baselines are retained but are not the selected immutable baseline. Their results must not be mixed with the final archive.

## Boundaries and next stage

This is a local deterministic scripted fighter. No model weights were trained, no Java UI/launcher was rebuilt, and no live Orion actions were issued. Existing PPO/RecurrentPPO checkpoints continue using their current separate environment.

EV comes from `runtimePlayerCombatDamageEstimate` with actual starter candidates and visible levels/equipment/prayers. The switch penalty is the versioned heuristic 0.25 expected-damage units per changed item. Static estimates do not predict future sampled damage or delayed prayer history. Incoming threat currently covers the visible ordinary weapon, not unseen gear switches or special multipliers. AGS expected-KO estimates against melee protection remain unavailable, so that preference conservatively blocks the special.

Stage 4 should generate versioned demonstration trajectories from this frozen teacher, capturing observations, precise eligibility, masks, actions and confirmed/rejected outcomes, with whole-episode splits and held-out seeds/opponents. No demonstration dataset or imitation training has been started.

Observations and unsaved telemetry stay in memory. The baseline output consists only of explicitly requested local synthetic metrics, code and hashes; there are no accounts, screenshots, external uploads or implicit browser storage.
