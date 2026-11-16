# Stage 1 audit and gate report

Date: 2026-10-14 (Europe/Amsterdam). Scope: controlled local NH starter simulator, existing DMM comparison environment, application/model bridge and read-only game viewer. No account login or live combat was performed. Stages 2–10 have not been implemented by this change.

## Stage-by-stage gap analysis

| Stage | Existing implementation and gap |
| --- | --- |
| 1 Audit/diagnose | Implemented structured current-state observations, intrinsic masks, execution results, explicit JSONL recording, integrity checks and exact replay. Critical adapter/readiness defects repaired. See evidence below. |
| 2 Starting rules | Reviewable proposal only: stage2-rules.proposed.json. No preference rules are active. User approval required. |
| 3 Scripted fighter | Source scripted opponents and some Orion-side managers exist, but no approved modular starter teacher or immutable 50-fight baseline. |
| 4 Demonstrations | Stage 1 telemetry records reproducible transitions. No 8,000/1,000/1,000 episode generation, coverage validator or split dataset exists. Diagnostic traces must not be silently used as held-out evaluation. |
| 5 Behavioral cloning | Not implemented. Existing small PPO models start without teacher imitation. |
| 6 Curriculum RL | PPO/RecurrentPPO run against the simulator. No gated curriculum or BC initialization; current PPO sampling does not consume the new masks. Executor guards reject invalid requests. |
| 7 Rewards/exploits | Terminal ±1 reward only. No effectiveness/resource/inactivity shaping or exploit suite. Short draws provide no useful success signal. No claim that reward changes improve fighting. |
| 8 Evaluation/promotion | Seeded checkpoint evaluation exists. No fixed multi-opponent benchmark, confidence intervals, best-model promotion, rollback dashboard or reliability gates. |
| 9 Manual practice | A read-only 3D spectator and 2D diagnostics exist. No human controls, coach mode or interactive replay/rewind. CLI deterministic replay is Stage 1 only. |
| 10 Orion transfer | Existing partial installed-client adapter and API checks exist. No validated 114-input observation/action mapping, replacement profiles, live combat parity or unattended authorization. Simulator policies remain ineligible for Orion control. |

## Inspected implementation

The application source is Orion-NH-Prototype, independent of packaged Owl.exe/_internal. It uses Java/Swing/C# for the launcher/companion, Python Gymnasium/SB3/PyTorch for policies and an owned Node process over JSON for NH combat. DMM uses pinned Python FastSim separately. NH source is NotCoco/nh-trainer commit e94c3ec3f6a7527fb0b12ded9c6e04c2a6810c21. Inspected starter inventory, native spell/weapon timers, consumables, prayer definitions, feature encoder, policy helper, actual actor state and advancement, model metadata gates, sampler, tests, lifecycle, UI, server and documentation. No nested project AGENTS.md was found; the Owl guide applies.

Runtime dependencies remain project-local and pinned by requirements.lock.txt and the Node package lock: Gymnasium 1.4.0, CPU PyTorch 2.14.1, SB3/sb3-contrib 2.9.0, Node 22.22.0, Three.js 0.184.0. No new runtime dependency was needed; tests use Python unittest and existing Java/browser tooling.

## Findings and fixes

| Classification | Evidence | Fix / residual gap |
| --- | --- | --- |
| Scheduling/simulator | Archived v1, seed 91: move_e2 plus melee moved 3 tiles, though 2 were requested (3096→3099). | Explicit movement and automatic route consumption share a tick budget; corrected regression ends at 3098. Cooldown/range are rechecked and rejected requests are explained. |
| Observation | Archived v1, fixed-melee seed 3, tick 30: real prayer=0 but encoded prayer feature=1.0 (full). The observation called a helper that refilled a temporary copy. | Read-only construction from actual actors, timers, supplies, movement and events replaces the action-applying helper. Depleted prayer and cooldown inputs now match state. |
| Execution | Repeated AGS requests called a toggle, flipping a queued special back off while waiting. Full inventory could silently rewrite AGS into WAIT. | Enable idempotently; report already-enabled; retain original request/decoded intent and reject blocked equipment/resource/attack channels explicitly. Regression confirms actual AGS launches. |
| Simulator resources | Normal prayer points did not drain. Zero points could still be assigned active prayers. | Explicit prayer OFF action, zero-resource guard, depletion/restore tests and a labeled OSRS average-drain model. Exact NH/Orion fractional-tick behavior remains unverified. |
| Scheduling/policy fairness | Opponent decisions were captured after applying the agent's same-tick action. | Both decisions are now captured from the same pre-action tick. Native combat PID ordering remains separate. |
| Validation | Scenario reset could partially change state before rejecting an invalid option. | Validate seed, ticks, opponent, permitted numeric fixture fields and target validity before resetting. Stale ticks/generations and malformed vectors never advance simulation. |
| Async application bridge | Integration test intermittently failed with a loaded policy but no first prediction. ready was published before inFlight was cleared; the first request could be dropped. | Readiness publishes only after load completes and the owned worker is alive. Test asserts a newly ready policy accepts its first request; real PPO/recurrent inference checks run. |
| Policy/reward | Eight deliberately simple diagnostic policies made 275 rejected attack requests (253 cooldown, 22 range) across 422 steps. They had 4 losses and 4 truncation draws. Fresh 128-step model checks also drew short fights. | This is diagnostic evidence, not a win-rate comparison or competent teacher. Demonstrations, masking in the sampler, curriculum and reward design remain gated future work. |

The passive prayer model uses the pinned prayer drain definitions and real equipped prayer bonus with rate/(60+2×bonus) per 600 ms tick, accumulating fractional points. The denominator/time relationship is documented in [RuneLite's primary PrayerPlugin source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/prayer/PrayerPlugin.java). This is an explicit OSRS-derived simulator approximation, not a claim that NH Trainer or Orion uses identical discrete timing. Runes/rune-pouch charge depletion are not modeled by the pinned core; actors carry the starter rune pouch. These assumptions must be calibrated before transfer and resource-efficiency claims.

## Contract, timing and privacy

Corrected schema: nh-browser-starter-sb3-v2 / nh-trainer-browser-starter-v2; 114 numeric inputs and [8,6,26,6,2] action dimensions. Prayer index 5 turns prayers off. Structured state is separate from debug results; no future rolled hits are fed into the policy observation. Full local-simulator information is explicitly labeled and must not be assumed available through Orion. Masks are intrinsic/factorized and cannot express every cross-channel interaction; execution revalidates the joint action after movement/resources. This is not yet masked PPO.

Each accepted step advances exactly one logical 600 ms tick. expectedTick and episode generation reject stale requests. Telemetry distinguishes accepted target/equipment operations from confirmed native attack/hit/supply events. No successful client submission is labeled a live server outcome. Sleep in the viewer is only presentation pacing, not action scheduling.

Recording is opt-in with a new JSONL path, preserves existing files and contains only synthetic simulator state. Default training stores no fight logs. Temporary test traces are removed on success/failure. Explicitly recorded partial traces survive interruption for diagnosis; no complete-episode success is fabricated. Hash chaining detects edits, not malicious authenticated provenance. Replay verifies the preset signature, exact bundle hash, observations, events, outcomes and rewards. No uploads, browser storage, real player names or account content were introduced.

Old NH v1 checkpoint files are preserved and evaluated only through --profile nh-starter-v1 with the archived original adapter. Its known defects are intentionally retained for comparison and excluded from the v2 gate. DMM remains separate. New v2 policies cannot be silently relabeled as v1 or Orion policies. Default train/evaluate seeds are separate (42/20042), with explicit overrides supported.

## Changes

- browser-source/nh-starter/worker.ts: actual-state encoder, legal masks, request/result auditing, atomic validation, movement budget, idempotent special, resource model and constraints.
- nh_starter_env.py: v2/legacy bridge, seed/epoch checks, reset fixtures, structured/mask access, opt-in trace lifecycle.
- fight_trace.py: JSONL integrity, engine/version checks and exact replay.
- audit/stage1.py, audit/test_stage1.py, audit/reproduce_before.py, audit/probe_legacy_observation.py: diagnostics, acceptance checks and original-bug reproductions.
- nh_worker.py: explicit recording, version compatibility and separate default train/evaluation seeds.
- browser-source/nh-starter/worker-v1.ts/.mjs and build-legacy.mjs: preserved original adapter for explicit comparisons.
- LearningAgent.java/LearningTests.java: readiness race repair and regression assertion.
- UiSmoke.java/BrowserFightSmoke.java: v2 smoke-checkpoint fixture paths; NHTrainerPanel.java: compatibility note.
- README files and stage2-rules.proposed.json: usage, assumptions, stage gaps and review-only preferences.

## Commands and executed evidence

From Orion-NH-Prototype (see audit/README.md for exact commands):

1. Existing test_nh_starter.py, test_adapter.py and test_visual_observer.py passed before changes.
2. Original Java build/API/model bridge checks passed before changes. A subsequent inference gate failed once; the readiness race was diagnosed and repaired rather than hiding the failure.
3. TypeScript static checks and build-nh-starter.mjs passed after simulator changes.
4. audit/test_stage1.py: **9 tests passed**, including movement, real observations, spell/target rejection, AGS idempotence, prayer cost/restore, stale input, atomic reset, default no recording and exact/tamper-tested replay.
5. Original NH starter tests passed on corrected v2: gear conservation, five-tick Barrage, actual AGS specials, randomized swaps, deterministic seeds and owned-worker shutdown/cancellation.
6. Eight diagnostic fights (seeds 9100–9107) produced **422 transitions / 71 confirmed agent attacks**. Exact replay passed for observations, native events and rewards. Artifacts: build/stage1-audit/2026-10-14-final/diagnostics.jsonl and summary.json. They are not a trained-policy benchmark.
7. Fresh v2 PPO: 128-step training, reload/evaluate two 32-tick fights, and exact replay of **64 evaluation transitions** passed; both fights drew. RecurrentPPO: 128-step training and reload/evaluation passed. These are pipeline checks, not competent policies.
8. Archived NH v1 PPO checkpoint evaluation passed without relabeling. Existing DMM/visual adapter regressions passed.
9. Application build, Java core/API checks, real Python toy PPO/recurrent inference and NH bridge checks passed after the readiness fix.
10. Headless Edge spectator checks passed with v2: live ticks, source/display alignment, executed attacks, hits, prayer icons and read-only route protection.

## Gate and next approval

**Stage 1 local audit/repair/replay acceptance gate: passed.** This gate does not assert full OSRS/NH/Orion mechanic parity, fighting competence, reward improvement or success of any later stage. The documented average prayer timing, unlimited rune charges, local full-state visibility and factorized mask limitations remain explicit compatibility/calibration work before advanced training and transfer claims.

Stage 2 is not started. The proposed-only JSON defaults are: normal eat at 55 HP, critical pressure at 35 HP, restore prayer at 20, restore Magic before Barrage when below its actual requirement, sensible protection counters with EV/switch cost for alternatives, and AGS at ≥50 energy with opponent HP≤35 and own HP≥60 plus reach/ownership/cooldown gates. These are initial learnable preferences; hard legal constraints remain outside the learner. Approve or change them before implementation, as required by the supplied specification.
