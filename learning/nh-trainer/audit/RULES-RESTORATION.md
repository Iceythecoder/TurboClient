# Game-rule restoration — fighter 1.0.3

The user explicitly requires fixed normal game rules and permits agent decisions/training changes, not gameplay modifications to make the agent win.

Removed:
- The custom route that granted a one-tile exit when attacking from the same tile.
- The control-layer direct tile update tied to that attack.
- Tests and rationale that relied on that forced behavior.

Restored route, attack eligibility, damage-independent control, equipment, supplies and prayer helpers now match the preserved pre-DD bridge exactly. The agent uses available ordinary movement actions to leave overlap when required by this simulator, then attacks. No alternative hidden routing is substituted.

Verification:
- All **73 pinned game src/ files** match their original Git blob hashes.
- Seven state-mutating/eligibility bridge helpers match the pre-DD source exactly.
- Three seeded action sequences, including stacked-player cases, match the original bridge's observations, resources, events and outcomes exactly.
- An attack with movement NONE grants no free step-out.
- **4 fixed-rule tests, 23 fighter tests, 1 watch-protocol test and 9 Stage 1 regression tests passed.**
- The 3D browser check passed with ordinary animated travel, correct DD overlap, actual combat events and read-only route protections.
- A fresh 50-fight reliability run passed: **34 wins, 16 time-limit draws**, zero learner rejections, missed decision deadlines or zero-healing supply uses, longest confirmed-attack gap 15 ticks.

Selected restored-rule archive: `build/stage3-baselines/2026-10-14-restored-rules-1.0.3`.
See `BASELINE-ELIGIBILITY.json`. Prior Stage 3 results are historical and must not be used as normal-game training or promotion evidence. No model or dataset was trained from those baselines.

Display animation is presentation only; it does not write positions back into combat state. This restoration verifies parity with the pinned source and pre-DD simulator, not full live Orion/server compatibility. The pre-existing prayer-drain approximation and rune-depletion limitation remain documented; no new calibration was introduced.

To apply the restored rules to an already running job: **Stop fight**, refresh the browser viewer, then **Watch fight** again. A running process retains the code it loaded before rollback.
