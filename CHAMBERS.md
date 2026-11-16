# Chambers / Olm integration

The configured rule-based Olm baseline is now described in [OLM-BASELINE.md](OLM-BASELINE.md). Its candidate profile is derived from the user-selected second demonstration and still requires Orion verification. The notes below describe the earlier standalone prayer milestone; they are not a claim that all Olm mechanics are solved.

Milestone 1–3 is rule-based projectile observation and optional prayer output inside the real Orion client. This is not learned behavior. It does not modify game rules, use a simulator, or implement full raids.

Restart OrionNH.exe, then open Chambers > Open Chambers teaching window > Projectile prayers.

The compact dashboard now keeps projectile tools under Advanced settings > Projectile prayers. Use current Chambers location captures the current instance template region and plane from the client and fills the region field. Click it only when you have personally verified you are inside Chambers. It also shows nearby numeric NPC IDs, without automatically classifying them as Olm. This is a user-taught region match, not independent proof of raid identity: other instances could reuse the same template, and a different Chambers room may need another capture. Instance mode alone is never accepted. These taught locations remain only in this companion session's memory and are cleared on companion shutdown. Closing Advanced settings retains taught locations but clears its separate prayer/NPC configuration. No location is uploaded or automatically saved.

Run readiness distinguishes a match to a taught Chambers location from a validated Olm encounter. A taught region alone never authorizes prayer or combat output. Olm still requires configured region + NPC checks; autonomous raid entry/combat remain unimplemented.

1. Click Apply + observe with empty settings to discover the current template region, nearby NPC IDs and projectiles targeting your actor. Discovery uses the installed client API, not screenshot recognition. Nearby NPC IDs are candidates, not proof they belong to Olm.
2. In a real Chambers/Olm encounter, verify the region and Olm NPC IDs and fill the corresponding fields. Both must match at runtime. Instance locations use WorldPoint.fromLocalInstance.
3. Match visible magic and ranged attacks to their observed projectile IDs. Enter one ID=MAGIC or ID=RANGED per line. The supplied image is a visual reference; it cannot establish numeric IDs. There are no guessed default IDs. Do not classify hazards as ordinary protectable attacks without testing.
4. Apply + observe again and check the proposed prayers against actual attacks before arming.
5. Arm prayer switching explicitly and focus the game canvas within ten seconds. F9 or STOP disables prayer output. Closing this window disables monitoring and clears its configuration and events.

Prayer selection uses the earliest mapped projectile whose interacting actor is the local player and whose floor matches. Equal-impact conflicting styles stop output. Unmapped incoming projectiles stop output. AoE/tile-targeted projectiles and melee attacks without projectiles are not handled. Projectile origin is not verified: the encounter gate is a configured region plus nearby configured NPC, not source-NPC attribution.

End-cycle minus current game-cycle provides an estimate of time remaining. Client cycles are nominally about 20 ms; Orion timing and the server's post-launch prayer checks are unvalidated. Prayers are requested immediately, not delayed to the safety margin. That margin flags late detection. Output is a native Prayers.toggle call with isPrayerActive checks and confirmation, not synthetic mouse input. Missing confirmation within five client cycles stops further output. Focus loss after acquisition, missing prayer points, logout, stale GameTicks and encounter exit also stop output. A recorded active prayer does not prove that the server protected a hit.

The in-memory event list is bounded to 40 entries; tracked projectiles to 128. Events contain synthetic decisions and numeric game IDs, no player names. No events/configuration are automatically written to disk or uploaded. Save teaching notes is an explicit separate operation, and does not save projectile configuration. No live gameplay has been recorded or trained by this feature.

## User plan retained for later implementation

Validate Chambers -> Overload(+)(4) sip -> Prayer enhance(+)(4) sip -> four Xeric's aid sips toward full HP -> Revitalisation sip -> enter Olm.

Phases 1 and 2: mage hand, then special attack and finish melee hand.

User-supplied phase cue: a confirmed green glow on Olm's neck indicates phase 3; a clearly visible neck without that glow indicates a regular phase (mage hand, then melee). An obscured or uncertain neck is UNKNOWN, not evidence of absent glow. The teaching window has a manual neck-cue selector and displays the corresponding plan; the label is included only when the user saves notes. This does not implement automatic visual recognition, prove the rule against Orion, distinguish phase 1 from 2, or execute phase-based attacks. A future detector must validate the neck region and avoid confusing green projectiles, scenery or prayer effects with the neck glow.

Phase 3: reduce melee hand to low remaining HP WITHOUT killing it. Damage mage hand, coordinate both hand deaths within Orion's actual allowed window, finish any remaining hand, then head. The user's clarification is low remaining HP, not 85–100% HP remaining. Do not interpret 100% damage as permission to kill prematurely. Stop threshold and kill window are unresolved. Target labels, NPC phase signals, potion doses/effects, HP checks, entry objects, movement hazards and gear presets all need validation before implementation. Four healing sips are a requested sequence, not evidence they always restore full HP.

Automatic potting, supply handling, entering rooms, attacking hands/head, phase detection, hazard movement, navigation and looting are NOT implemented by this milestone. No changes are made to mechanics to make the plan succeed.

## Validation

ChambersTests uses mocked API objects and replaces prayer output with an in-memory collector. It tests configuration parsing, region+NPC gates, target filtering, impact ordering, unknown/conflicting projectiles, active/pending prayer handling, stale ticks, focus loss and leaving the encounter. It does not establish live prayer success. UiSmoke renders the new controls offline. Bootstrap self-test includes ChambersTests.
