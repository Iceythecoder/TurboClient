# DD position confirmation and cooldown cycling — fighter 1.0.2

Changes:
- DD entry is a request until an observed post-execution state places both fighters on exactly the same tile with the opponent still frozen. Adjacent positions do not consume a successful DD opportunity.
- DD may repeat once per confirmed attack cooldown during the same freeze; it is no longer limited to one entry per freeze.
- The spectator snaps entry/exit transitions to actual state positions, avoiding a full-tick interpolation lag that could show separation while the state said overlap.
- Fighter cards show each actor's tile and their distance. DD confirmation requires distance zero. Requested moves and confirmed overlap are labelled separately.
- Exit continues to use attack intent, with no additional policy walk-away action.

Validation:
- 23 native Stage 3 tests passed, including rejection of adjacent-tile confirmation, repeated DD within one freeze and attack-only exit.
- Full watch-mode seed 9300: 197 frames, 131 with the opponent frozen, 86 showing actual same-tile DD. Every DD-confirmed label was checked against exact coordinates.
- Stable frozen DD occurred for 61 ticks in the native test, with **zero new opponent attacks** during those ticks. Six previously queued hit impacts still arrived. DD therefore does not imply that HP cannot drop, and step-out/entry processing windows can expose the agent.
- Headless browser test checks the actual Three.js rendered actor roots and the visible distance-zero card at DD confirmation. It passed, alongside attacks/hits/prayers and source/display alignment.
- The 50-fight reliability evaluation produced 37 wins and 13 time-limit draws, zero learner rejections, missed deadlines or zero-healing supply uses, and a longest confirmed-attack gap of 16 ticks. This is local simulator evidence, not a claim of live Orion performance.

Final archive: `build/stage3-baselines/2026-10-14-dd-position-final-1.0.2`.
Earlier source/results remain preserved as historical versions.

To load both fixes: stop the running fight, refresh the browser viewer, then start a new fight. Running Python workers retain their starting policy; loaded browser pages retain their starting JavaScript until refreshed. No Java application restart is needed.
