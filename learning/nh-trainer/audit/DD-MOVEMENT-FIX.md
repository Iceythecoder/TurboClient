# DD movement animation correction

The prior exact-position fix snapped DD entry/exit to destination tiles, which visibly teleported the fighter. The spectator now interpolates travel over part of the display tick, plays walk for one-tile movement and run for longer movement, and faces the travel direction while moving. Movement animation takes precedence while travelling instead of leaving a previous cast pose active.

DD status shows **Entering DD** while the model travels and **DD confirmed** only when rendered roots overlap and the native state agrees. The tick's tile coordinates remain destination/state coordinates. Attack-only step-out and fighter decisions are unchanged.

The browser integration check samples DD mid-travel, requires an intermediate position and walk/run animation, rejects premature DD confirmation in both card and caption, and then verifies exact rendered overlap on arrival.

Refresh the browser viewer to load the rebuilt animation code. No application restart or policy retraining is required.
