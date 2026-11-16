# AI TOA Fighter prototype

`TurboTOA.jar` owns the ToA observer, instance profile and Warden prototype. It is
loaded beside `TurboChambers.jar`, but it never mutates the Chambers profile or
learning data.

Advanced settings exposes one set of setup controls:

- **Load instance data** copies only the Chambers gear/resource baseline, records
  the current room position as the obelisk blocking tile, and observes the carried
  Ghrazi rapier and Elder maul.
- **Save instance data** validates and saves `profiles/toa/instance-data.json`.
- **Load pre-saved data** restores a validated ToA-only profile.

Load instance data while standing on the intended obelisk blocking tile and while
the Ghrazi rapier and Elder maul are present in inventory/equipment. The copied
MELEE uses the positively charged Scythe for every normal melee target. CORE is a
dedicated Ghrazi rapier and Avernic defender switch used only while the Warden core
is exposed. SPEC uses the Elder maul for the opening obelisk special. Chambers remains
unchanged.

Run prototype uses the normal visible Inventory, Prayer, Combat, movement and NPC
controls. Potion steps advance only after one dose is observed to disappear. Gear
is limited to four visible item requests per game tick. The policy uses:

1. imminent final-Warden floor-slam movement (absolute priority),
2. other configured movement hazards,
3. protection prayer,
4. exact pre-pot order and 80 HP / 40 Prayer guards,
5. gear and offensive prayer,
6. DPS.

The prototype covers Osmumten Begin, the obelisk and one Elder-maul special,
charged-Scythe follow-up, Elidinis Magic/Ranged alternation, Rapier/Avernic core windows,
Tumeken floor patterns, melee Energy Siphons, configured Zebak/Ba-Ba signals and
Ranged Tumeken DPS. Stable NPC role IDs have conservative defaults. Orion-specific
projectile, animation, graphic and scene-object IDs remain editable under the
`mechanics.signals` object and should be confirmed with a walkthrough.

Start walkthrough remains available for calibration. It records bounded numeric
game state, projectile/graphic/animation signals, passive scene-object changes and
explicit mechanic notes. It does not record chat, player names, credentials or
send data externally. Walkthroughs save only when the user chooses.

F9/global Stop ends both observation and prototype actions and clears unfinished
visible-control targeting.
