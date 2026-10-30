# 1.19.1 — Schematic builders no longer stall

Symptom (08_watch_tower): builders finished the lower cobblestone core and then stood still with the top centre block missing. Single dirt blocks were left around the base, and the scaffolding was never removed.

## Causes

1. **Stand positions excluded the cells they needed.** Any cell still to be built, from the current layer up to two layers above it, was forbidden for standing or pillaring. A solid core block enclosed on four sides can only be clicked from on top of the structure, so those blocks had no stand at all.
2. **Reach was checked against the block centre, not a clickable face.** From the side, the centre looked reachable even though walls hid every face that could be clicked.
3. **Stands were re-chosen every 4 s while pillaring.** A builder abandoned its half-built pillar for a new spot, which left the stray one-block dirt pillars.
4. **Builders could stop overlapping the target cell.** Arrival tolerance is 0.45 blocks, so a builder could end up inside the cell it was trying to fill and be blocked by itself.
5. **Failed blocks waited up to 20 s between retries** even when no other work existed, so every builder stood idle.
6. **Unsupported blocks were claimed one layer early.** A plank platform with nothing beneath it yet produced pointless support columns.

Scaffolding was never removed because removal only begins once every block is placed, and the build never got there.

## Fixes

- `StandFinder` aims at the actual clickable faces, the same ones `SchematicPlacer` clicks. Cells still to be built now cost a detour instead of being forbidden. It searches from 6 below to 2 above the target, can pillar up to 10 blocks, and skips stands that already failed for this block. It is now written against a small `View` interface so it can be tested on a block grid.
- `SchematicBuildTask` keeps one stand until it is reached or its route fails. At the stand it sneaks onto the block centre before placing.
- `BuildSchedule` offers blocks that are cooling down when nothing else is available. Retries still count, so an impossible block is skipped quickly instead of making builders wait. The maximum backoff is now 10 s.
- `SchematicJob.claimNext` takes blocks that have a solid neighbour first. It never takes an unattached block one layer ahead.

`SchematicStandTest` rebuilds the tower in a block grid and checks the missing top centre and every block of the next core layer. It also covers avoiding a stand that already failed, other builders' reserved spots, a block with nothing to click, removing a scaffold pillar, and retrying cooling blocks.
