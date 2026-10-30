# 1.20.0 — Furnishings last, doors and held blocks

## Furnishings are placed last

Schematic builds now run in three phases. Structure goes first, then fittings (ladders, doors, torches, beds …), then furnishings. Furnishings are chests (including trapped and ender chests), barrels, shulker boxes, crafting tables, crafters, furnaces, smokers, blast furnaces, anvils, enchanting tables, brewing stands, stonecutters, grindstones, looms, lecterns, composters, cartography/smithing/fletching tables and jukeboxes. `PlacementRules.stage` classifies each block. `BuildSchedule` now holds any number of ordered phases instead of a two-phase flag. A finished block that goes missing is still rebuilt before later phases continue.

## Doors and gates (all companions, all tasks)

Builders got trapped inside finished houses because of how a closed door is shaped. It is a 3/16-block panel along one edge of its cell. The pathfinder's walkability test checks a body box centred in the cell (0.2–0.8), which never touches that panel. The closed door cell therefore looked walkable, the route took a plain step through it, and the companion pushed against the panel indefinitely. Scaffolding outside was never reached.

- `Passages` identifies doors and fence gates. `WorldNeighbors` turns a closed, hand-openable door or gate into an `OPEN_DOOR` step and never a plain walk. Iron doors and gates are treated as walls.
- Before each step, `NavigationController` opens any closed door or gate in the current or next cell, using the existing empty-hand interaction. It records the doors it opened and closes each one once it is clear of the doorway and no living entity stands in it. Doors that have been open for 10 s are left open.
- `StandFinder` never chooses a doorway as a place to stand.

## Held items

`SchematicBuildTask` puts the block it is about to place in the main hand while walking and placing. Navigation still switches to the scaffold block only for the instant it pillars or bridges. During scaffold removal the companion carries the right tool. While waiting, its hand is stowed (empty).

## Tests

`SchematicBuildTest` adds tests for phase order with fittings and furnishings, structure being reopened after a later phase, classification of furnishings, the door, iron-door and gate rules, and the vanilla door-panel geometry that caused the trap.
