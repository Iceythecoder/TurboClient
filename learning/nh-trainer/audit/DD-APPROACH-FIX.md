# DD approach correction — fighter 1.0.1

The previous fighter permitted deliberate DD only when already one tile from a frozen opponent. Its ordinary Magic/ranged spacing kept it around four tiles away, so many freezes never resulted in DD.

The movement planner now approaches a confirmed frozen opponent during attack cooldown using cache-checked movement candidates, then enters DD when reachable. It requires enough remaining freeze time, available own movement, no current supply priority and an unused DD opportunity for that freeze. The next attack still routes out automatically; the policy sends no extra walk-away command.

The viewer's decision phase distinguishes DD approach and DD entry. A running fight retains the policy it started with: stop it and start another to load the updated Python fighter. No Java rebuild or application restart is needed.

Validation:
- 20 Stage 3 tests passed, including actual DD approach from four tiles, own-freeze blocking, native attack-only exit and previous deadlock regressions.
- The exact app watch protocol produced 257 frames in a full seed-9300 ranged fight: 166 with the opponent frozen and 21 showing actual player overlap while frozen. The test checks coordinates and decision rationale, not just a requested DD command.
- A fresh 50-fight baseline passed its reliability gate: 31 wins, 19 time-limit draws; zero learner action rejections, missed deadlines or wholly wasted supply uses; longest confirmed-attack gap 12 ticks.
- These results establish the behavior and reliability, not improved combat strength.

New archive: `build/stage3-baselines/2026-10-14-dd-approach-1.0.1`.
The earlier immutable 1.0.0 baseline remains preserved; its reproduction must use its archived code.

Run the viewer regression from the project root:
```powershell
$env:PYTHONPATH=(Resolve-Path learning\nh-trainer).Path
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\test_dd_watch.py
```
