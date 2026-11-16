# Browser equivalence verification — 2026-10-14

Result: NOT VERIFIED. Current local scripted fighter is not a browser-control agent.

## Checks actually performed
- Loaded https://nh-train.com/ in a fresh isolated Edge browser using Playwright.
- Clicked NH stake and Start, then opened the prayer and inventory tabs through normal UI controls. No game-state injection, practice boosts, saved setup, existing browser profile, or game-source edits.
- Public UI and WebGL shell loaded without page exceptions. At the sampled frame the UI still reported loading neural opponent, so this run does NOT establish a running fight, combat success, or agent performance.
- Reran audit.test_fixed_rules: four tests passed, all 73 pinned original game source files matched. This establishes only the checked local source and original-action parity, not parity with today's hosted release.

## Why the equivalence claim fails the current evidence
- No screenshot-based observation adapter or normal browser mouse/keyboard action adapter connects scripted_fighter.py to the public site. Browser viewer tests are spectator tests only.
- MovementPlanner reads exact native positions and freeze_until_tick. These are engine facts, not visual estimates from normal player observations.
- StarterTeacherEnv.visible_estimates queries the native engine; damage estimates use actual self and opponent actors, including native combat statistics, rather than estimates inferred solely from visible equipment. Naming this API visible_estimates does not make it a player-visible observation.
- The bridge exposes native legal-action masks and supply eligibility and applies action vectors directly. Normal UI clicks and recognition timing have not been measured.
- The public site's documented opponent is a trained Hard model. The local 50-fight baseline mixes simpler scripted opponents and mirror fights. That baseline cannot establish public-opponent performance.

## Required before a PASS
A separate browser adapter must obtain player-available observations, infer freeze timing and tile placement, calculate estimates without reading hidden opponent stats, and deliver ordinary mouse/keyboard inputs. Record observation and action provenance, prohibit practice boosts and game-state writes, then measure actual fights against the public opponent. Keep visual/control failures visible. No changes to game rules are needed or authorized for this work.

Artifacts: inspection.json and initial.png next to this report; repeatable script at learning/nh-trainer/browser-source/verify-public-browser.mjs. The screenshot and text are from this isolated public simulator session only.
