See the [Stage 3 scripted fighter and frozen 50-fight baseline](audit/STAGE3-REPORT.md), including confirmed-freeze DD and automatic step-out from attack intent. [Stage 2](audit/STAGE2-REPORT.md) covers approved starting rules; [Stage 1](audit/STAGE1-REPORT.md) covers the preserved v2 engine and replay. Existing models do not use the teacher yet.

# NH Trainer training connection

The **NH Trainer** tab uses the pinned [nh-train.com project](https://github.com/NotCoco/nh-trainer) locally. **NH starter (NH only)** is the default: its normal NH equipment and inventory, with the source browser combat engine. **DMM (legacy gear)** preserves the old FastSim environment for existing DMM checkpoints. Python, Node, renderer code and game assets are installed inside this project; no browser extension or external account is required.

The application does not remotely control nh-train.com, upload Orion observations or modify the public website. **Open nh-train.com** opens the public practice site separately.

## Use it

1. Restart `OrionNH.exe` to load the new release. A running older client continues using its original JAR; the launcher selects the tested release on the next start.
2. Open **NH Trainer**, leave **Training setup** at **NH starter (NH only)**, then **Check training connection**. The check verifies pinned source hashes and runs eight actual combat decisions.
3. Select PPO or RecurrentPPO and an opponent: fixed ranged/magic/melee or seeded rapid switching. NH starter uses local scripted practice opponents; DMM uses upstream scripted curricula. Neither is the public website's pretrained Hard neural opponent.
4. Choose the training step count and maximum fight length. Defaults are 8192 steps and 256 logical ticks per fight. CPU training may take time; progress is shown every 128 decisions.
5. Click **Start simulator training** and choose a **new** `.zip` filename. NH starter needs a fresh checkpoint; it cannot reuse a DMM checkpoint. A sibling `.json` records the setup, schema, starter-item signature, source commit, action labels, opponent, seed and reward. Existing checkpoints are preserved. Your choice explicitly saves these output files; fights/rollouts otherwise stay in memory.
6. Choose **Evaluate checkpoint** to run that trusted checkpoint against the selected opponent. Results show wins/losses/draws for simulator fights. This does not establish Orion skill or compatibility.
7. **Cancel job** terminates the application's own simulator subprocess. Closing the companion does the same. F9 stops Orion combat; simulator jobs have their own cancel control.

Training/check/evaluation controls disarm Orion. The simulator does not need an Orion target or account login. No simulated actions are forwarded to Orion, and no policy is automatically activated after training.

## What is trained

### NH starter: default

Both fighters start with the exact `RUNTIME_NH_STAKE_EQUIPMENT_ENTRIES` and 28 `RUNTIME_NH_STAKE_INVENTORY_SLOTS` from upstream `src/ui/runtimeSetupPresets.ts`. Gear is restricted to those worn and carried items throughout the fight:

| Role | Starter equipment |
| --- | --- |
| Magic | Staff of the dead, Mystic top/bottom, blessed spirit shield |
| Ranged | Dragon crossbow, Karil's top, Verac's skirt, blessed spirit shield |
| Melee | Abyssal tentacle, Karil's top, Verac's skirt, avernic defender |
| Special | Armadyl godsword; shield must be stowed before its two-handed equip |
| Shared | Neitiznot helm, imbued Saradomin cape, fury, Barrows gloves, Dragon boots, Seers ring (i), opal dragon bolts (e) |

Supplies are the source preset's manta rays/anglerfish, brews, Sanfew, combat/bastion potions and rune pouch. No Torva, Virtus, Masori, Zuriel's, Zaryte, Voidwaker, VLS, halberd or Gmaul is owned in this setup. Item conservation and a strict allowed-gear check apply to both fighters. It changes actual equipment and bonuses; it is not a cosmetic replacement in the viewer.

`nh_starter_env.py` exchanges in-memory JSON with the owned `browser-source/nh-starter/worker.mjs` Node process. This adapter uses upstream `runtimePlayerCombat.ts`, source consumables, equipment definitions, timers, freezes, special attacks, map collision, and the upstream 114-input feature encoder. Its PPO/RecurrentPPO action dimensions are `[8,6,26,6,2]`: attack/equip/wait/AGS, prayer, movement, supply, and full-style versus weapon-only switches. The learner's choices are applied directly through native combat operations rather than overwritten by scripted counter/style helpers. Reward is terminal win/loss only.

The adapter is a headless practice loop, not the full interactive browser runtime. Routing, per-tick decisions and inventory integration remain adapter responsibilities; equivalence to every browser/server behavior is not established. Barrage timing, AGS launches, inventory conservation, seeds and lifecycle were exercised against this integration. The public production training/reward pipeline is not reproduced.

Metadata uses `nh-browser-starter-sb3-v2` / `nh-trainer-browser-starter-v2`, records the full starter preset and its signature, and keeps `live_control:false` / `validated_orion:false`. A checkpoint with a different setup/schema is rejected before policy loading. Custom replacement items are not implemented yet; they should become explicit profiles with their own signatures and validations. Item substitutions alone do not establish Orion transfer.

### DMM: preserved legacy option

The adapter uses the upstream **114-input FastSim DMM** observation and channel semantics. The learned policy selects combat/spec intent, protection prayer, movement, supplies and optional per-slot gear choices. The native engine applies legal-action constraints, gear dependencies, resources, cooldowns, freeze rules, hit timing and movement.

PPO's action space has four native core channels plus eleven optional gear channels. Optional gear adds the upstream virtual NONE choice; inactive ammo has only NONE. Metadata stores the exact action dimensions and native action IDs. This differs from both the public browser's model format and our earlier 10-input style-only Orion demo policies. Checkpoints must not be relabeled or silently loaded through a different adapter.

`nh_env.py` pauses the original `Engine.step()` at `policy.score()`, its real decision boundary. The observation is therefore built **after the upstream timer/hit-arrival stages and before the submitted action**, preserving the engine's ordering and delayed opponent observations. Each action advances one logical 600 ms tick; simulation is not paced by wall-clock sleeps. Source files are unmodified.

The initial PPO/RecurrentPPO setup uses **upstream terminal reward only**. NH Trainer's production model training also uses a specialized pipeline and causal reward/event data; this integration does not reproduce that pipeline or provide its pretrained Hard model. A short smoke run proves that training/evaluation work, not that the resulting policy is a competent fighter. Longer training and held-out opponents are still needed.

For the DMM option, the loadout and world remain the upstream DMM configuration with the committed world-35 map asset. Select **DMM (legacy gear)** to evaluate existing DMM models. Orion gear presets and prompt imports do not rewrite simulator items, bonuses or mechanics.

## Watching fights and progress

In the application's NH Trainer tab, enable **Watch current fight** and select **Game view (browser)** before starting training or evaluation. The viewer opens in your default browser and shows the same local fight as animated, cache-derived game characters in the simulator's terrain, with equipped items, overhead prayer sprites, HP bars, hit effects and supply/cooldown statistics. Drag to rotate and scroll to zoom. **Open fight viewer** reopens it during a job. **2D diagnostics** remains available for inspecting tiles and return history.

The game view uses the pinned upstream model-composition, animation and map-coordinate renderer modules with Three.js. It is a spectator UI, not the complete interactive public browser client. FastSim exposes executed attacks and landed hits but does not supply the full browser-client packet/effect stream; effect timing and animation presentation are reconstructed. Not all special/bolt/consumption/death visuals are reproduced exactly, and this is not a claim of pixel-level parity. Damage, positions, equipment, prayer state and fight outcomes come from the actual training engine. Visual instrumentation is checked against an uninstrumented native engine for identical observations, rewards, HP and terminal outcomes.

The default display delay is **600 ms** to watch at the simulator's logical tick pace. Use 100 ms for a quick readable view or 0 for maximum training speed; at 0 the latest-frame viewer skips intermediate frames. This changes wall-clock pacing, not combat rules; watching with a delay slows training. Closing the viewer does not cancel training; use Cancel job to stop it.

Training repeats fights and updates policy weights in batches. The viewer distinguishes collecting experience from updating weights; evaluation uses fixed weights and does not learn. Completed-fight counts, wins/losses/draws and rolling mean returns help follow behavior. Training returns alone do not establish improvement: evaluate successive checkpoints against the same opponents and settings. One fight is too little to judge dependable progress. Frames and the bounded return history stay in memory and are not recorded.

## Compatibility and privacy

Both simulator profiles retain `live_control:false` and `validated_orion:false`; the Orion policy loader rejects their incompatible schemas. A separately validated Orion observation/action adapter is required before these policies can control Orion. No provenance flag or simulator score proves compatibility.

Training is local over the application-owned subprocess. The optional game viewer uses a read-only HTTP server bound only to `127.0.0.1` on an ephemeral port, behind a random per-session URL. It serves only viewer assets and the latest synthetic fight snapshot; there is no upload, gameplay-control or filesystem-write endpoint. Browser responses disable caching, and the viewer uses no browser storage, external scripts, analytics or recordings. Synthetic fight state stays in memory. Closing the companion stops its server and workers. Only explicitly chosen model outputs are retained. Source/tool/package files are downloaded from their publishers; no gameplay is uploaded.

Load only trusted local checkpoints: SB3 checkpoint files may contain pickle objects. The evaluator checks checkpoint tensor dimensions, metadata, native action IDs and source commit before using them.

## Source and license

Source: `https://github.com/NotCoco/nh-trainer`, commit `e94c3ec3f6a7527fb0b12ded9c6e04c2a6810c21`. Exact downloaded-file hashes are in `source-manifest.json`. This is a minimal simulator dependency checkout, not the full browser build. Run `install-source.ps1` to restore missing pinned files; it preserves differing existing files rather than overwriting them.

Renderer and NH combat modules are under `browser-source/`, with Git blob checksums in `renderer-provenance.json`; `install-browser-source.py` verifies downloads against the pinned tree. NH jobs verify the source combat, generated-definition, bot and starter-preset files. The custom spectator is `browser-source/spectator/`, bundled in `spectator-dist/`; the NH adapter is `browser-source/nh-starter/`.

From `browser-source`, use the project-local Node to run `node_modules/typescript/bin/tsc -p nh-starter/tsconfig.json`, `build-nh-starter.mjs`, `node_modules/typescript/bin/tsc -p spectator/tsconfig.json`, and `node_modules/vite/bin/vite.js build --config spectator.vite.ts`. The browser viewer does not import the public client's analytics or browser preferences.

The upstream `LICENSE` and `NOTICE.md` are retained under `upstream/`. The MIT license applies to original project code; it does not grant rights to third-party game assets. This setup is local and has not been published or redistributed.

## Validation performed

- Pinned source integrity and actual engine connection.
- Native current/next decision observation alignment, reward alignment, tick order, deterministic reset, terminal state and cancellation (`test_adapter.py`).
- Upstream combat-formula, prayer-timing and freeze-duration checks.
- Short PPO **and** RecurrentPPO training runs followed by checkpoint reload/evaluation.
- Application Java/Python connection and cancellation checks.
- Application build and offline GUI render verification.
- Real live simulator/browser integration in headless Edge: progressing ticks, aligned snapshots, executed attacks, landed hits and prayer sprites; token, traversal and read-only endpoint checks (`browser-source/test-spectator.mjs`).
- Native-versus-instrumented trajectory equality (`test_visual_observer.py`).
- NH starter preset/gear restrictions for both fighters, gear-item conservation through randomized swaps, 5-tick Barrage, actual AGS special launches, deterministic resets, normal shutdown and abrupt owner-cancellation cleanup (`test_nh_starter.py`).
- New NH PPO and RecurrentPPO training/reload/evaluation, plus rejection of DMM checkpoints in NH mode; legacy DMM evaluation remains available.

These validate the integration and tested upstream behavior; they do not validate parity with Orion or all simulation mechanics.
