# 1.19.0 — Cooperative schematic building

`/bot schematic build <file>` makes every ordinary companion in your dimension build one Sponge `.schem` file together. Command details are in [COMMANDS.md](../COMMANDS.md#schematics).

## Where files come from

In single player (integrated server) the server directory is the game instance, so files go in `<instance>/schematics` (for example `.minecraft/schematics`). A dedicated server reads `<server>/schematics`. The world's `schematics` folder and `config/worldedit/schematics` are also searched.

## Pathfinding: Baritone-style, server side

Baritone is a client mod. It drives the single local client player through client input. These companions are server-side `ServerPlayer`s with no client, so Baritone cannot control them. A dependency on it would also force every server to run a client-only mod.

The build uses the mod's own server-side pathfinder, which works the same way as Baritone's. It is an incremental, tick-budgeted A* whose moves include walking, jumping, falling, swimming, climbing ladders, opening doors, breaking obstacles, bridging, and pillaring by jumping and placing a block underneath. Schematic builds add three things to it:

- **A dedicated scaffold item.** Every pillar or bridge block that navigation places during a build uses one temporary block type that the schematic does not contain. The build records each one for removal.
- **Protected finished blocks.** Navigation may not dig through blocks the build has already finished.
- **Stand positions.** `StandFinder` picks a cell within reach and line of sight of each target. That cell is either already standable or reachable by pillaring up to six blocks from solid footing. It never picks a cell that must receive a block soon or that another builder has reserved. Navigation then walks or pillars there.

## Components

| Class | Role |
|---|---|
| `schematic/NbtReader`, `Schematic` | Pure-Java gzip NBT reader for Sponge v1–v3. Sizes are bounded at 64 MiB of NBT, 16.7 M cells and 131,072 placed blocks. |
| `SchematicPlan` | Parses block states against the 26.2 registries and applies rotation. Each placement gets an item and a count (two for double slabs). Door tops and bed heads merge into their first part. Unbuildable states are listed. Planning runs off the server thread. |
| `PlacementRules` | Decides which blocks are structure and which are fittings placed last. It also sets the click-support order and placer yaw for each block, and which properties count as a match. |
| `BuildSchedule` | Pure shared queue. Structure is built before fittings, lowest layer first. A builder may work one layer ahead only above finished blocks. Claims are exclusive. Failures back off, and a block is skipped after six attempts and revived once at the end. |
| `SchematicJob` | The shared job. It tracks claims and their expiry, stand reservations, one footprint strip per builder, just-in-time material accounting, the scaffold registry, re-verification of finished blocks, layer announcements and the 60 s job watchdog. |
| `SchematicPlacer` | Places with the real item through vanilla `useItemOn`. It then corrects orientation or variant, and has a last-resort direct set that still consumes the item. |
| `task/SchematicBuildTask` | Each builder's state machine: claim, clear the cell, supply, approach, place, then remove scaffold. It parks off the footprint while it waits, has a 10 s stall watchdog and a breakout, and recovers from errors itself. |

## Validation

The 14 unit tests in `SchematicBuildTest` cover NBT/varint parsing for v2 and v3, rejection of corrupt data, layer and phase ordering, claim exclusivity, backoff, skip and revive (no deadlock), deferral, reopening, strip assignment, planning, rotation, state matching, support order and the default origin. All 20 sample files in the instance's `schematics` folder parse and plan without errors. In-world building has not been run here: that needs a Minecraft server whose EULA the operator has accepted, and a connected client.
