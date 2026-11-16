# Stage 2 — starting rules and conflict checks

Status: implemented and acceptance checks passed on 2026-10-14.
The user approved the Stage 1 proposal before implementation.

## What changed

- `rules/nh-starter-1.0.0.json`: approved versioned configuration, with native hard constraints separated from editable starting preferences.
- `starting_rules.py`: strict loader, SHA-256 configuration identity, deterministic read-only advice, decision reasons, and stale/masked/unowned/conflicting action preflight rejection.
- `audit/test_stage2.py`: 18 tests covering boundaries, conflicts, invalid configuration, estimate provenance, and actual native supply execution.
- `audit/stage2.py`: read-only seeded simulator preview; no fight actions, saved traces, model updates, or network calls.
- Root and NH Trainer README introductions updated. The original proposal remains a historical artifact.

Approved configuration hash: `49fb9e7b844329dfad8a334d1b1bd15ea066a631d1d292d8f8a148f46007db63`.

## Behavior and priority

1. Native eligibility and owned equipment remain mandatory. Advice never confirms a hit or replaces native execution validation.
2. Critical HP (35 or lower) or a supplied credible visible KO estimate takes priority over restoration and attacking. It recommends panic food/brew when legal, then available fallback supplies.
3. Restore at prayer 20 or lower, or Magic below 94, before normal eating/reboosting. Restoration requires actual Sanfew stock; the factorized mask alone can misleadingly allow this channel through combat/bastion stock.
4. Normal eating starts at HP 55 or lower. High-HP partial waste/unjustified overhealing is suppressed when waste avoidance is enabled. Threat-driven high-HP eating requires explicit current visible evidence and the approved permission preference.
5. Opponent missile protection favors Magic; Magic protection favors ranged, with melee chosen only when reachable and supplied expected damage minus switch cost makes it advantageous. Melee protection and no protection use supplied expected damage/cost comparisons. Missing evidence is explicit; the advisor holds rather than fabricating optimal damage.
6. Visible starter weapon IDs select defensive protection. Unknown weapons are marked unknown; unavailable prayers recommend OFF.
7. Freeze control is preferred beyond melee reach when available and not countered by Magic protection. The last three remaining frozen ticks produce preparation advice. Native five-tick immunity is respected; preparation is not an instruction to cast through immunity.
8. AGS requires actual starter ownership, legal native mask, distance at most one, energy at least 50, own HP at least 60, opponent HP at most 35. Against melee protection it also requires an explicitly supplied expected-KO estimate.
9. Any supply recommendation suppresses attack and AGS advice. Native food delays and brew-induced level changes still require execution-time checks.

Action indices match the existing v2 descriptor. Advice supplies are 0=none, 1=safe_eat, 3=brew_only, 4=restore_reboost, 5=panic_full. Defence indices are 0=Magic, 1=missiles, 2=melee, 5=OFF.

## Validation

From the project root in PowerShell:

```powershell
$env:PYTHONPATH=(Resolve-Path learning\nh-trainer).Path
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\test_stage2.py
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\test_stage1.py
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\stage2.py --hp 35 --prayer 10
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\stage2.py --hp 99 --prayer 20
```

Results: **18 Stage 2 tests passed; 9 Stage 1 tests passed**.
Native integration executed actual food/brew, prayer restoration and Magic restoration, checking consumption telemetry. Cooldown/depleted-prayer advice was checked against native masks.
Read-only previews recommended panic supply at 35 HP and restoration at 20 prayer, respectively, with attack/spec suppressed.

An initial integration assertion expected status `confirmed`; native supply telemetry actually uses `accepted` plus reason `consumed`. The assertion was corrected against the existing source. No engine change was required.

Numeric threat/expected-damage tests use explicitly supplied test estimates to exercise thresholds and ranking. They do not establish a validated damage estimator. Other boundary fixtures copy native snapshots and vary individual fields; native integration is a separate test.

## Limits and next stage

This stage does not modify Java UI, simulator engine, saved policies, reward function, or installed clients. Therefore no executable rebuild or model retraining was needed. Stage 1 bundled-engine hashes and replay compatibility remain unchanged.

The advisor has no continuous fight loop and does not override PPO or control Orion. Estimates must be supplied by a source-backed visible combat estimator in Stage 3; missing estimates stay missing. Counter descriptions are versioned alongside evaluator semantics: changing their text requires updating the evaluator. Numeric thresholds are validated editable preferences, not learned weights yet.

The factorized preflight mask is necessary but insufficient for joint legality. Native execution still owns collision, supply timing, shield stowing, attack range after movement, prayer history and actual outcomes. No rune-depletion or NH/Orion prayer-drain parity claim has been added.

Stage 3 should compose these rules into a deterministic modular starter fighter, connect source-backed estimates, implement movement and resource scheduling, and run the fixed 50-seed baseline. Demonstration generation and imitation/PPO training belong to later stages.

Rules and transient observations remain local and in memory. Preview output goes to the requesting terminal; the component creates no logs, datasets or browser storage.
