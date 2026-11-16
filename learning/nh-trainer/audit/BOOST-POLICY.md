# Potion boosting policy — fighter 1.0.4

The agent now uses Super combat and Bastion through ordinary owned-inventory inputs. Native potion effects, brew drains, stock, dose conversion and cooldowns are unchanged.

Policy:
- Emergency food and requested restoration take priority.
- Inspect current native combat levels against native base levels and native potion caps; do not store a permanent boosted flag.
- Restore drained base stats with Sanfew before reboosting. Brewing's existing attack/strength/ranged/Magic drain is observed directly.
- Use Super combat when Attack or Strength is at least five levels below its potion cap.
- Use Bastion when Ranged is at least five levels below its potion cap. Prefer Bastion first for a ranged counter; otherwise prefer Super combat.
- Use only available doses when native consumable eligibility allows. Avoid redrinking at full boost.

The previous combined restore/reboost input could not independently choose Bastion while Super combat remained. The adapter now accepts an explicit `inventoryUse` selection on the existing potion-use channel, limited to Sanfew, Super combat and Bastion. It calls the same native consumption function in the same control ordering. Omitting this selection preserves the original shortcut exactly. No game src/ file or potion effect was changed.

Validation:
- 7 native potion tests passed: real dose/boost changes, both potions used once at full boost, brew reducing actual boosts, restore-before-reboost, native lock rejection without spending a dose, supply priority, missing stock, and malformed input rejection.
- 4 fixed-rule tests passed: all 73 pinned src/ hashes match; existing native behavior is preserved when no specific inventory input is given; no free DD step-out.
- 23 fighter regressions passed. DD-only scheduling fixtures disable optional boosting to isolate movement; seeded full-fight tests and the 50-fight run use boosting enabled.
- Watch-protocol and browser tests passed, including actual inventory changes and DD rendering.
- 50-fight baseline: 38 wins, 12 time-limit draws, zero learner action rejections, deadline misses or zero-healing supply uses; longest confirmed-attack gap 32 ticks.

Archive: `build/stage3-baselines/2026-10-14-native-boosts-1.0.4`.
The restored-rules 1.0.3 baseline remains a valid historical comparison. Earlier custom-routing baselines remain excluded from normal-rule training evidence.

Stop the running fight and start another to load the updated agent; no application rebuild/restart is needed for this Python/Node policy update.
