# Turbo Gauntlet

Turbo Gauntlet is an independently loaded Orion feature. Its source is under
`features/gauntlet`, its module is `TurboGauntlet.jar`, and its host and launcher use
separate release pointers and session files. Closing or rebuilding this feature does
not modify Chambers, Slayer, ToA, or ToB profile data.

## Current workflow

1. Open `Turbo Gauntlet.exe`.
2. Choose Regular, Corrupted, or Auto Detect.
3. Start **Observation & Diagnose** and perform a manual run.
4. Stop and save the diagnostic trace only if you want to retain it.
5. Review the candidate NPC, object, projectile, graphic, and item IDs. Candidate IDs
   are observations, not automatically trusted mappings.
6. Open **Signal registry** and mark a signal `VERIFIED_RUNTIME` only after the Orion
   run proves that mapping. OSRS reference values must remain `OSRS_REFERENCE` until
   independently observed in Orion.

No chat, player names, credentials, or external network uploads are recorded. Frames
and events remain in memory unless the user explicitly saves a trace. Closing the
module clears the in-memory trace.

## Architecture

- `GauntletModule` owns the independent UI, bounded observer, privacy behavior, and
  signal registry.
- `GauntletState` is a deterministic state machine covering validation, preparation,
  gathering, crafting, demi-bosses, Hunllef, reward, exit, recovery, and stop.
- `GauntletPlanner` stores a run-local discovered room graph and provides a
  time-expanded tile planner. It checks every intermediate tile against future floor
  activation and predicted tornado reach; a safe endpoint cannot hide an unsafe path.
- `GauntletActionContract` separates requested actions from observed results. State
  may advance only after its postcondition is observed, with bounded retry, timeout,
  stale-generation cancellation, and hard failure.
- `GauntletResourceLedger` accounts for required, collected, reserved, spent, and
  available materials so two crafting plans cannot spend the same resource.
- `HunllefTracker` counts only mapped projectile attacks, ignores duplicate movement
  events and stomps, predicts the four-attack style transition, and separately tracks
  the six qualifying player attacks used for overhead prediction.
- `GauntletSignals` records provenance for each mapping: verified Orion runtime,
  OSRS reference, hypothesis, or unknown.
- Gameplay output may only go through the shared `RaidInputBridge`; direct client
  mutation and patched Orion classes are not used.

## Build

```powershell
./build.ps1 -Publish gauntlet
```

The build compiles Java 11-compatible bytecode, runs the common suite and Gauntlet
synthetic tests, creates `TurboGauntlet.jar` and `TurboGauntletHost.jar`, writes the
two Gauntlet release pointers, and builds `Turbo Gauntlet.exe`.

## Safe control gate

The current build deliberately remains observation-only until all critical Orion
signals are verified. Even after a signal registry is complete, the action-selector
acknowledgement adapter remains gated until a live Orion validation run confirms the
correct resource, door, crafting, prayer, weapon, reward, and exit interactions. This
is intentional: the project does not silently substitute OSRS IDs or claim an
untested full run.

