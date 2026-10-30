# 1.23.0 — Go around, else break through and seal

When a builder cannot reach a block, because walls, a roof or a cluster of finished blocks are in the way, it escalates in a fixed order.

## 1. Go around and scaffold (priority)

The first spot is chosen as before. Once a spot has failed, `SchematicBuildTask.approach` adds +10 to every spot over the building's footprint (`SchematicPlan.overFootprint`). The next choice is then a spot outside the walls, which navigation reaches by pillaring up the outside on scaffold. Sealing a gap always uses this outside preference.

## 2. Break through

After a second failed spot, a "no route" result from navigation, or a stalled builder, `breakThrough()` lets that builder's routes break finished schematic blocks for 30 s (`SchematicJob.allowBreach`, `BREACH_TICKS` = 600). Breaking a finished block costs `BREACH_COST` = 40 per block (`ConstructionPolicy.breakCost`), roughly three pillar blocks, so A* still walks or scaffolds around whenever that is workable. The job-wide 60 s watchdog also grants this to every builder.

## 3. Seal at the end

`WorldActionController.breakBlock` reports every block a companion breaks (`ConstructionPolicy.broken`). A finished schematic block broken while building is recorded as a breach. It stays open as a passage for everyone and is not rebuilt straight away. When everything else is built, every breach is reopened and placed again with the schematic's block (`SchematicJob.sealing`), from outside the footprint when possible. Scaffold removal starts after that.

A block can be sealed this way at most twice (`MAX_SEALS`). Blocks broken during scaffold removal, or beyond that limit, are rebuilt by normal verification instead. Chat reports each break-through ("broke through … it will be sealed at the end") and the sealing round.

## Boxed-in cells

A cell solid on all six sides cannot be seen from anywhere. Such a cell now only needs to be within reach: `StandFinder` skips the visibility test for it, and escalated placement sets it directly, still consuming the item.

## Tests

`SchematicStandTest` adds tests that a boxed-in cell gets a working spot, and that with the outside preference a missing wall block is worked from outside the house. Recording breaches and sealing them uses the live world and needs an in-game check.
