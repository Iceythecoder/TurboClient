# Installed Orion API audit

Inspected local files on 7 October 2026:

- `C:\Users\ruben\AppData\Local\Orion\Orion.jar`: a launcher; manifest main class `net.runelite.launcher.Launcher`.
- `C:\Users\ruben\.orion\repository2\client.jar`: actual client and bundled dependencies/API. Used as this prototype's compile/runtime library, copied unchanged into `runtime/orion-client.jar`.
- `C:\Users\ruben\.orion\cache\injected-client.jar`: packaged game client; preserved.

Signatures were read using the installed `javap` tool, including relevant method bytecode. Names below are present in the installed library. Their presence and compilation do not prove runtime/server behavior.

| Capability | Existing hook/API | Prototype use / remaining uncertainty |
| --- | --- | --- |
| Server/client ticks | `events.GameTick`, `events.ClientTick`, `Client.getTickCount()` | Actual event-driven timing; 600 ms remaining budget is an estimate |
| Current opponent | `Client.getLocalPlayer()`, `Actor.getInteracting()`, `Client.getPlayers()` | Explicit lock of current interacting Player; identity/index/generation checks |
| Prayer overhead | `Player.getOverheadIcon()`, `HeadIcon` | MELEE/RANGED/MAGIC/NONE; other values UNKNOWN |
| Target health/location | `Actor.getHealthRatio()/getHealthScale()/getWorldLocation()/isDead()` | Health-bar percentage; no exact remote HP |
| Opponent equipment | `Player.getPlayerComposition().getEquipmentId(KitType.WEAPON)` | User-configured weapon-to-style mapping |
| Inventory/equipment | `Client.getItemContainer(InventoryID.INVENTORY/EQUIPMENT)`, `Item.getId()` | Capture actual equipment, skip already equipped, check availability |
| Equip actions | `Inventory.getFirst(int...)`, `Item.hasAction(...)`, `Item.interact(String...)` | Installed interaction helpers; action acceptance needs live tests |
| Active prayers | `Client.isPrayerActive(Prayer)`, `Prayer.getWidgetInfo()`, `Prayers.toggle(Prayer)` | Skip active prayers, inspect widget, wait for activation before attack |
| Barrage | `SpellBook.Ancient.ICE_BARRAGE.canCast()`, `Magic.selectSpell/isSpellSelected` | Installed resource/spellbook checks; Orion-specific resource conventions still unverified |
| Cast on Player | `Player.interact(int,int)`, `MenuAction.WIDGET_TARGET_ON_PLAYER` | Same sequence used in installed `Magic.cast(Spell,Player)` bytecode; no coordinate targeting |
| Normal attack | `Player.interact("Attack")` | Locked player only; action availability checked |
| Attack confirmation | `events.AnimationChanged` | Explicit verified local animation mappings; not proof of damage |
| Special energy/state | `Combat.getSpecEnergy()/isSpecEnabled()/toggleSpec()` | Existing helper API; energy/action semantics need live validation |
| Freeze-related graphics | `events.GraphicChanged`, `Actor.getGraphic()` | User-configured duration estimates; no authoritative freeze hook established |
| Current movement | `Actor.isMoving()` | Displays movement observation; does not invent movement permission |
| Integration | `RuneLite.getInjector()`, `EventBus.register/unregister`, `ClientThread.invoke`, `OverlayManager.add/remove` | Companion started in a new process using public APIs |

## Loading and unchanged existing files

`PluginManager.loadPlugins` contains an explicit allowlist of plugin class names. The prototype **does not modify that allowlist**, patch an installed class, install itself as an external plugin, disable attach protections, or attach to a running process. Its bootstrap starts `RuneLite.main()` from the local packaged client, obtains the existing injector and registers its own companion event handlers. Startup through this path remains unverified beyond the offline `--help` entry point. A client update could invalidate these APIs.

No existing Orion/Owl files need modification. The companion is isolated in `Orion-NH-Prototype` and the launcher starts that prototype's local copy of the installed library.

## Modules/files

| File | Modules |
| --- | --- |
| `CombatCore.java` | TickManager, TargetManager, GearManager, FreezeManager, AttackManager, SpecialAttackManager, DecisionEngine, ActionScheduler, TelemetryManager; adapter contract and data objects |
| `OrionAdapter.java` | Actual installed-client adapter, OpponentStateTracker, LocalPlayerStateTracker; prayer and spell operations behind the adapter |
| `CombatRuntime.java` | Event subscriptions, target validation, lifecycle, stop behavior, in-game debug overlay |
| `PrototypePanel.java` | Configuration, current-equipment capture, mapping editor and telemetry display |
| `ConfigManager.java` | Explicit preset save/load; no persisted arm flag |
| `LearningAgent.java`, `AgentPanel.java` | Local async PPO/RecurrentPPO bridge, mode/checkpoint management and toy training UI |
| `KnowledgePrompt.java`, `PromptInfoDialog.java` | Supported information parsing, preview and explicit rule application; no natural-language model |
| `learning/agent_worker.py`, `learning/combat_env.py` | Local policy inference/training and a toy style-selection environment, not an Orion combat environment |
| `LearningTests.java` | Actual subprocess inference for both demo algorithms, prompt import, schema/model gates, stale-state checks and shutdown |
| `NHTrainingConnection.java`, `NHTrainerPanel.java`, `NHTrainingTests.java` | Local NH Trainer job connection, training/evaluation UI and real subprocess checks |
| `BrowserFightServer.java`, `BrowserFightSmoke.java`, `learning/nh-trainer/browser-source/spectator/` | Read-only loopback game spectator using pinned upstream character models, animation and terrain modules; actual simulator snapshots and executed-attack/landed-hit instrumentation; reconstructed visual effects, no pixel-parity claim |
| `learning/nh-trainer/nh_env.py`, `nh_worker.py` | Gymnasium decision-boundary bridge to pinned upstream FastSim; separate 114-input training schema, not Orion control |
| `learning/nh-trainer/nh_starter_env.py`, `browser-source/nh-starter/worker.ts` | Default NH starter profile: exact upstream starter gear/inventory for both fighters, source browser combat/feature encoder, bounded owned Node process, separate checkpoint schema and preset signature; no DMM gear or scripted style overrides |
| `Bootstrap.java`, `Launcher.cs` | New-process startup; no installed-file changes |
| `CoreTests.java`, `IntegrationTests.java`, `UiSmoke.java` | Pure logic tests, installed-API/config checks and offline UI rendering |

PrayerManager and SpellManager are currently adapter operations rather than separately instantiated services; extract these before extending spell/prayer policy. The combat core does not depend on Orion classes.

## Missing endgame hooks / unimplemented behavior

1. Confirm Orion weapon speeds, local/remote attack animation IDs, graphics, special requirements and durations. These are explicit configuration, not guessed tables.
2. Establish authoritative freeze and movement-permission signals, immunity and pathing. Current freeze estimates cannot replace them.
3. Validate dynamic prayer/spell widgets, unlock requirements, staff rune substitutions and accepted interaction order in live Orion.
4. Establish stronger attack/cast-result confirmation (e.g. correlated projectile/action results); configured animations are only a prototype signal.
5. Determine whether equipment and prayer confirmation can complete inside one observed tick on this client/server. A missed deadline must not be claimed as a same-tick success.
6. Add movement/pathfinding and broader attack-range/line-of-sight rules. The prototype restricts melee and lets the server resolve ranged/magic reachability.
7. The new-process bootstrap now passes an actual login-screen/companion-window startup check. Account login and combat remain untested. Overlay registration/removal must run on Swing's UI thread: game-thread registration before login triggered a null-varbit exception in Orion's `WidgetOverlay$XpTrackerWidgetOverlay.getPosition`.
