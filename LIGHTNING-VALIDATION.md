# Lightning movement validation

Automatic run-through is disabled unless both `runAcrossLightning` and
`lightningCrossingTimingVerified` are explicitly enabled. Existing profiles do
not have verified timing. Leave verification false: the experimental crossing
branch is not a validated tile-skip implementation.

The latest inspected saved attempt showed a collision: the player approached
an initially untracked wall and occupied its tile at tick 291. Distance alone
was insufficient. Fresh walls now reserve possible nearby next positions until
motion is observed. Pending crossing destinations are rechecked for hazards
instead of suppressing electric avoidance while waiting.

The user's proposed tile skip requires a stationary, aligned start, running
enabled, a dangerous middle tile, and a clear destination two tiles away.
Moving lightning additionally requires measured Orion movement and damage
timing. These conditions have not been validated live. Safe-row avoidance is
the current default; do not enable the experimental branch based on offline
test success.

Saved attempts include bounded movement/action diagnostics and observed
overhead/run state to distinguish a requested click from actual movement.
Offline overlap regressions cover the recorded approach pattern, unknown
motion, safe-row holding, and the verification gate. They do not establish
successful avoidance in a live raid.

## Stationary mage-hand correction

The next inspected death showed repeated `DODGE_FALLING` requests while actor
graphic 2856 was repeatedly relabelled at the player's new position. This
candidate is no longer automatically classified as a falling crystal, even in
old profiles with `useOrionCrystalSignals` enabled. It requires the separate
`orionActorCrystal2856Verified` flag, false by default. Explicit configured
graphics and installed crystal/projectile signals remain active. Orion-specific
red-circle detection therefore still needs independent calibration; this
change does not claim all red-circle variants are now verified.

Holding a safe camp continues to override optional walking. Necessary escapes
prefer an adjacent safe tile in attack range, without a bonus for moving closer
to the hand. Arrival statistics remain recorded but cannot bias dodge direction;
explicit user-labelled corrections remain tie-breaking preferences. Offline
regressions replay the persistent-graphic pattern and confirm actual hazards
still trigger a dodge, followed by holding the newly safe position.
