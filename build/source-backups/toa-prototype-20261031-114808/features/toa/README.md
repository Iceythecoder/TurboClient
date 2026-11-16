# Orion raid modules

`TurboChambers.jar` contains the existing application/shared controls and Chambers
fighter. `TurboTOA.jar` is an independently compiled/loaded ToA module, not a copy
of the Olm strategy. Both use the same installed Orion API and main launcher.

The main window opens AI Olm Fighter, AI Slayer Fighter and AI TOA Fighter.
ToA currently supports a bounded, read-only walkthrough recorder. Combat is
disabled until its encounter rules are implemented and validated. No existing
Chambers NPC IDs, potion sequences, target model or hazard rules are applied to ToA.

Data separation:

- Chambers keeps its existing `profiles/olm-profile.json`, `profiles/attempts`,
  learned presets and starter marker. These paths are not migrated or overwritten.
- ToA walkthrough save dialogs default to `profiles/toa/walkthroughs`.
- ToA's in-memory observations/notes are owned only by the ToA module and never
  passed into Olm learning. Closing the companion clears unsaved ToA data.
- ToA saves nothing automatically. Stop observing and explicitly Save walkthrough.

Start walkthrough records tick/cycle timing, local position, HP/prayer, inventory
and equipment IDs/quantities, active prayers, nearby NPC identities/health bars,
projectile/graphic IDs and local/NPC animation events. Menu actions contain numeric
IDs and action options; player names, chat and login fields are not captured.
Mechanic notes are whatever the user types and are saved only with the walkthrough.
No uploaded data or automatic gameplay actions are part of this module.

F9/global Stop stops ToA observation too. Starting a ToA walkthrough stops other
combat automation first so it observes manual play. Closing/hiding the ToA window
stops observation but leaves the unsaved recording available to save/discard.

The build produces immutable release versions of both JARs. `release-current.txt`
selects TurboChambers, while `release-toa.txt` selects the matching ToA module.
Current convenience copies of both JARs also exist at the application root.
Launch Turbo Client.exe rather than opening module JARs directly. Updates include
both artifacts and preserve each user's existing raid data. Impact is independent
and is not changed by this split.
