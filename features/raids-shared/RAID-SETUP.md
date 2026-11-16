# Separate raid fighters and matching setup controls

The main window opens AI Olm Fighter, AI Slayer Fighter, AI TOA Fighter and
TOB AI Fighter. The raid artifacts are TurboChambers.jar, TurboTOA.jar and
TurboTOB.jar. ToA and ToB each load through their own classloader and own
their settings/observations independently. Their shared setup UI source is
under features/raids-shared, compiled separately into each module.

Each raid offers the same three configuration controls in Advanced settings:

- Load instance data
- Save instance data
- Load pre-saved data

For ToA/ToB, Load instance data loads that raid's observed-candidate.json if
present. Otherwise it captures the current room position, nearby NPC IDs,
inventory/equipment and available gear presets. ToA additionally makes an
independent copy of the Chambers supply families, 80 HP / 40 Prayer guards,
three-tick potion cooldown, offensive prayers and exact pre-pot order. It observes
the carried Ghrazi rapier and Elder maul, replaces the copied Dragon hunter lance
only in the ToA MELEE/SPEC presets, and leaves Chambers unchanged. No Olm target,
phase, hazard or learning data is imported.

Save instance data validates and applies edits, then saves instance-data.json
under that raid's own data directory. Load pre-saved data chooses and validates
the appropriate raid schema before applying it. A ToA file cannot be loaded
as ToB data or vice versa. No configuration control sends gameplay actions.

ToA: profiles/toa/instance-data.json and profiles/toa/walkthroughs/
ToB: profiles/tob/instance-data.json and profiles/tob/walkthroughs/
Chambers: existing Olm profiles/learning remain in their existing locations.

ToA has a conservative Warden prototype behind its validated instance profile.
ToB remains observation-only. Both retain walkthrough recording, mechanic notes,
Stop/F9 and explicit save controls. User-entered notes are saved as typed; chat,
player names and credentials are not automatically captured.

To prepare a walkthrough, open the appropriate fighter, use Advanced settings
to load/save instance data, then Start walkthrough. Play manually, mark relevant
mechanics, stop observing, and Save walkthrough. Starting observation stops any
other bot action first. Independent recordings and settings are not uploaded.
