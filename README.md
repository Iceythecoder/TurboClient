The main application now includes **NH Trainer → Watch NH fighter**, using the restored-rules fighter, independent opponent/count controls, and the same live 3D viewer as the standalone preview. Start with one fight at 600 ms per tick.

Stages 1–3 of the gated NH AI roadmap are implemented. See the [Stage 3 scripted fighter, frozen baseline and tests](learning/nh-trainer/audit/STAGE3-REPORT.md). The local teacher has passed 50 seeded fights; existing trained models and the application UI remain separate.

# Orion NH prototype

Open **OrionNH.exe** to start a separate Orion client with a companion configuration window and in-game overlay. It uses your installed Java runtime and a local copy of Orion's actual client library. It does not attach to an already-running client. Close an existing Orion session yourself if you want to replace it; this app never terminates one.

This is an incremental prototype, not a verified complete autonomous PvP controller. Compilation, pure combat tests, installed-API checks, offline GUI rendering and startup to Orion's login screen with the actual companion window were tested. Account login and live combat execution were **not** tested.

Startup repair: overlay registration/removal now runs on Swing's UI thread, matching Orion's plugin lifecycle. Registering it on the game thread before login previously triggered a null-varbit failure in Orion's native XP-tracker overlay. The optional debug HUD cannot prevent the companion window opening. Startup also waits for a usable game canvas and reports exception locations in selectable text; exceptions stay in memory rather than persistent logs.

The **Learning agent** tab now includes locally installed PPO and Recurrent PPO, checkpoint loading, asynchronous recommendations, toy-demo training and a **prompt / information window**. Both demo policies have been trained and tested through the actual Python worker. They are not Orion PvP-trained and cannot control live combat. The prompt window imports supported weapon/animation information and configuration; PPO does not interpret freeform chat. See [learning/README.md](learning/README.md) for activation and supported formats.

The **NH Trainer** tab defaults to **NH starter (NH only)**: the normal NH fight's starter equipment and inventory for both fighters, using the pinned browser combat engine. It excludes the previous DMM gear and supports PPO/RecurrentPPO training, evaluation and cancellation. Start a fresh NH checkpoint; existing DMM models remain available under **DMM (legacy gear)** and are rejected in NH mode. Enable **Watch current fight**, choose **Game view (browser)** and leave the delay at **600 ms** to watch the actual training fight. Some visual effects are reconstructed; **2D diagnostics** remains available for tile/return inspection. This does not automate a hosted website session or forward simulator actions to Orion. Item replacements and validated Orion transfer remain future work. See [NH Trainer setup](learning/nh-trainer/README.md).

## First test

1. Open `OrionNH.exe`, log into Orion and keep **observation mode** enabled.
2. In the companion's Equipment tab, select MAGIC. Equip your intended magic setup manually in Orion, then choose **Capture current equipment**. This reads actual equipped item IDs; it needs no typed names or screenshots. Remove items you do not want to switch. Repeat for RANGED, MELEE and optionally SPEC. Save the preset only if you want it retained.
3. Make sure each setup's unequipped items are in your inventory. Open Ancient Magicks once and check Ice Barrage availability, runes and spell level.
4. **Auto-lock on current target** is enabled by default. Attack a player normally, or manually cast Ice Barrage on them: the app detects the selected attack and locks that same actor once Orion confirms your current interaction. Follow, Trade, NPC attacks and other player-targeted spells do not acquire a PvP lock. Attacking a different player cancels the old queued sequence and recurrent context before acquiring the new confirmed target. It does not arm automation for you. The actor's object identity, index and lock generation are retained; it disarms when the opponent disappears/dies or an attack intent is not confirmed within two seconds. Uncheck auto-lock to use **Lock once** instead. The option is retained only when you explicitly save a preset.
5. Change the opponent's overhead protection in a controlled test. Check the overlay and Observe/run telemetry for detected prayer and proposed counter. Unknown/combined overhead prayers are not treated as no protection.
6. For a preparation-only test, leave **Automatic attack/cast**, **Offensive prayers**, **Defensive prediction**, **Special attacks** and **Freeze estimates** off. Use **Apply settings + arm**, then immediately focus Orion's game canvas. It will equip the configured counter and select Barrage for Magic, leaving you to attack manually.
7. **F9** stops inputs while a window in this Orion process is focused. The panel also has a STOP button. After arming, you have 10 seconds to focus the game canvas; no inputs are sent while waiting. Subsequent loss of game-canvas focus disarms the prototype. Configuration changes disarm it. Input already submitted to the client cannot be undone.

## Automatic attack testing

Automatically attacking requires verified local attack-animation IDs and their attack speed in ticks. Manually attack using a setup and observe the local animation ID in the status panel. On Signal mappings, record that animation while it is visible, using the selected setup's configured speed. Verify you recorded an attack, rather than movement, eating, or another animation. Repeat for the weapons you intend to use.

Enable Automatic attack/cast only after validating these mappings. Attacks wait for required equipment to appear in the equipment container and for the offensive prayer if enabled. An attack submission starts a cooldown; a configured local attack animation confirms the attempt. No confirmation within 1.8 seconds disarms the controller. Animation confirmation is evidence of a combat action, not proof of damage or a successful freeze. All casts/attacks retain the locked actor.

## Timing

The clock uses actual `GameTick` events and `Client.getTickCount()`, with `System.nanoTime()` for monotonic deadlines. The time remaining is estimated from a **600 ms** duration measured after the event arrives; it is not knowledge of the server's internal arrival deadline. Network jitter and processing order can affect it.

The default one-window deadline attempts the current observed tick. The scheduler submits at most one queued action per client-state event, then reevaluates its preconditions on the next event; it does not burst potentially competing interactions or insert fixed sleeps. Strict equipment confirmation can miss this deadline if equipment updates arrive on the next server tick. Choose two or three windows during testing if needed; these are explicitly **not** one-tick results. Failed sequences disarm. Partial equipment changes may remain after a deadline or cancellation. No random-delay behavior from the previous app is carried into this prototype.

## Optional behavior

- Offensive prayers: selectable by combat style; already active prayers are skipped. Widget availability and actual activation are checked before attacking.
- Defensive prediction: supply enemy weapon ID or attack-animation ID mappings to MAGIC/RANGED/MELEE. Unknown signals produce no prediction. An observed animation may be too late to protect against that hit; this is not guaranteed early prediction. No guessed weapon table is included.
- Freeze estimates: supply confirmed Orion graphic IDs and durations in ticks. Graphic events start estimates for the local player or locked opponent. Estimates influence Barrage scoring near expiry. They do not verify that a freeze landed, immunity, movement permission or path reachability. There is no authoritative `canMove`/`isFrozen` hook established yet.
- Special attacks: capture SPEC equipment and choose attack style, approximate opponent health-bar percentage and minimum spec energy. Opponent health is derived from health ratio/scale, not exact HP. Correct special activation/consumption on Orion remains unverified.
- Style choice: configurable preferred counters plus switch-count cost, range, Barrage availability and optional freeze priority. Melee requires distance <= 1; no automatic chasing or movement is implemented. A legal alternative can be chosen if the preferred setup is unavailable.
- Continuous attacks: when enabled, reevaluation occurs on controlled tick/prayer events and confirmed cooldowns. Gear/spell preparation without automatic attacks reacts to prayer transitions.

## Privacy and installation

The companion has no screenshot capture, recording, uploads or persistent combat telemetry. Target names and live stats appear in the UI/overlay only. Saving a preset explicitly stores equipment IDs, rules and signal mappings. Arming is never persisted. Inherited client file logging is disabled in this launched process with `prototype-logback.xml`. Orion itself still uses its normal cache/configuration/session files and connects to its game services; the companion does not change those retention behaviors.

Original `Owl.exe`, its `_internal` directory, the installed Orion launcher/client and the previous `Orion-AHK` project are preserved. The local client copy is for this computer's test build; rebuild after Orion updates. The tool provides no detection evasion or ban protection.

## Build

The current Olm prayer/scheduling update, actual API inspection, test procedure and remaining encounter gaps are documented in [Reactive Olm agent](OLM-REACTIVE-AGENT.md). Offensive prayers now default to Augury/Piety/Rigour for the corresponding Olm combat styles; profiles can override or disable them. Prayer timing can be explicitly saved and loaded from Advanced settings → Projectile prayers, including after an unsuccessful attempt. Live protection efficacy remains unverified.

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1`. The build uses the installed Java 21 compiler with Java 11 compatibility, the actual local Orion library and the installed Windows C# compiler. It requires no dependency downloads. `API-AUDIT.md` lists inspected capabilities, module boundaries and missing hooks. `endgame-specification.txt` preserves your requested endgame.

Builds are staged under `build/releases/`; `release-current.txt` and `release-runtime.txt` select the tested artifacts for the launcher. This permits an update while an older client is holding its JAR open. Existing releases and running clients are preserved. Restart the application to load a newly selected release.
