# Tormented demons: reference baseline and outstanding verification

Checked 2026-10-24. These are OSRS reference mechanics, not verified Orion behavior. No numeric baseline below is used to drive the current Slayer fighter.

| Mechanic | OSRS reference | Orion status |
| --- | --- | --- |
| Normal attack interval | 6 ticks | Unknown |
| Defensive prayer | Changes after 150 damage; six-tick attack pause | Unknown |
| Fire bombs | Approximately 60 ticks; player tile plus one adjacent walkable tile; two ticks to escape after release | Unknown |
| Offensive style | Changes after bombs; inaccessible melee can use ranged/magic | Unknown |
| Shield | 20% reduction with weapon exceptions; one unshielded hit after initial attack/bombs | Unknown |
| Defenseless window | Approximately 30 ticks after cycle begins | Unknown |
| Multi-demon behavior | Distinct staggered attackers; additional bomb threats | Unknown |

References: [OSRS Wiki monster](https://oldschool.runescape.wiki/w/Tormented_Demon), [strategy details](https://oldschool.runescape.wiki/w/Tormented_Demon/Strategies).

Before enabling: verify Orion animation/projectile IDs and impact timing, exact style-selection rules, NPC overhead API, bomb collision tiles, shield signals and bonus-damage formula. Configure at least two offensive setups. No fixed OSRS cycle should substitute for missing observations.

Current implementation: the profile selector explicitly rejects starting Tormented demon automation. Standard NPC mode supports current equipment, chosen prayers, and HP/prayer supply targets. It has no demon-specific switching, bomb avoidance, loot or banking.