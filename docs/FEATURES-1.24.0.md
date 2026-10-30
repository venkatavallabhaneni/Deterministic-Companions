# 1.24.0 — Plan the build before building

A schematic build now has a **PLANNING** stage between site clearing and construction:

```text
CLEARING → PLANNING → BUILDING (structure → fittings → furnishings → sealing) → CLEANUP → DONE
```

## What the plan decides

For every block, in the exact order the build runs (phase, then layer, then ring, walking to the nearest next block like a builder would), `BuildPlanner` decides:

- **Where to stand.** It uses the same `StandFinder` the builders use live.
- **How to place it.** Most blocks are clicked against a neighbour's face. A block with nothing clickable from any reachable spot is marked **direct**: the builder aims at the cell itself and places or sets it, still consuming the item, without first using up attempts.
- **Which scaffold it needs.** When no footing is in reach, the planner adds a scaffold tower: a column from the ground up to the stand. The tower goes into the model as solid, so later blocks reuse it. Towers prefer spots outside the footprint, and the planner avoids standing in or pillaring through cells that are still to be built.

Each placement is counted as **from the ground**, **from the structure**, **from a scaffold tower** or **direct**. Blocks with no planned spot at all are reported too.

## How it runs

- **Snapshot.** The site, plus 6 blocks around the footprint and from 6 below to 3 above it, is copied into a `VoxelModel` at 4,096 cells per tick. Each cell is recorded as solid or not, and lava, fire and unloaded cells as hazards.
- **Planning thread.** `BuildPlanner.plan` runs on a background thread over that copy, so the server never waits. `VoxelModel` does line-of-sight with an exact voxel walk (Amanatides–Woo).
- **Starting the build.** The job applies the result and reports it, for example "plan ready — 679 blocks: 512 from the ground, 140 from the structure, 27 from 2 scaffold towers (14 scaffold blocks)". If planning fails, construction starts anyway, with live searching for every block.

## How builders use it

- **Planned stand first.** A block's planned stand is tried before live searching. If that spot fails for that block, live searching takes over, as before.
- **Direct blocks.** Blocks planned as direct go straight to aim-and-place.
- **Nearby work first.** When claiming, a block whose planned stand is within one block of where the builder already stands is strongly preferred, so builders finish everything reachable from a tower or wall top before moving. A block whose planned stand another builder has reserved costs extra.
- **Scaffold.** Scaffold is still placed by navigation on the way up and recorded for removal, so towers appear where the plan put them.

`/bot schematic plan` prints the plan summary and the first scaffold tower positions. `/bot schematic status` includes a plan line.

## Tests

`BuildPlannerTest` covers:
- ray casting;
- a one-storey house planned without scaffold;
- a 12-high tower using at most 4 shared towers, all outside it, each serving many blocks;
- the same input always giving the same plan;
- a block with nothing to click being planned as direct.
