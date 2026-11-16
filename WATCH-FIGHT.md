**Live inventory:** watched starter fights now open a small 28-slot Agent inventory window. Icons and quantities follow actual simulator snapshots; yellow highlights show changed slots. Use **Agent inventory** to reopen it, or toggle **Keep on top**. Available in both the main application and standalone preview. Restart the app/preview once to load the newly built release.

Main application: restart `OrionNH.exe`, open **NH Trainer**, choose **Fight opponent** and **Fights**, then click **Watch NH fighter**. Use **Stop fight** to cancel. Defaults are one fight and the existing 600 ms viewer pace; no checkpoint or connection check is required. The game rules remain the restored pre-DD rules.

The current fighter is 1.0.3 with [restored original rules](learning/nh-trainer/audit/RULES-RESTORATION.md). Agent movement uses ordinary available inputs; the custom forced DD step-out has been removed. Stop any old fight and start another to load the restored rules.

DD confirmation now requires **Distance 0 tiles**. After updating, stop the fight, refresh the browser, and start another. See [DD position correction](learning/nh-trainer/audit/DD-POSITION-FIX.md).

# Watch the current Stage 3 fighter

Double-click `Watch-NH-Fight.cmd` in the Owl folder (or the project folder).
The standalone preview does not launch or attach to Orion.

1. Choose an opponent. `fixed-melee` is useful for observing freezes and DD; `mirror` runs the same fighter on both sides.
2. Leave the tick delay at 600 ms for real-time viewing.
3. Click **Watch fight**. Your default browser opens the local 3D game viewer.
4. Watch gear, overhead prayers, attacks, hits, HP, supplies and freeze/cooldown state. The preview window shows decision reasons.
5. **Stop fight** cancels the owned simulator. Close the preview to stop its job and local viewer server.

Each run is one fight, up to 256 logical ticks (about 2.5 minutes). A time-limit draw is possible. This is the current scripted baseline, not a model learning during the fight.

The rebuilt main application's **NH Trainer** tab also has **Watch Stage 3 fighter**. It uses the selected opponent, fight count, length and viewer speed. No checkpoint or training-connection check is needed for the scripted viewer. Restart the main app to load the newly built release; an already running client keeps its existing version.

This feature runs locally and does not save fights or screenshots. Display snapshots remain in memory. The browser server is loopback-only and read-only; it does not contact the public practice site.

Validation: watched scripted CLI run completed; Java build/core/learning/NH bridge checks passed; the headless browser rendered advancing Stage 3 ticks, actual attacks, landed hits and overhead prayers, with source/display alignment and route protections verified.
