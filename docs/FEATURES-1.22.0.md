# 1.22.0 — Site clearing before construction

A schematic build now begins with a **CLEARING** stage (`SchematicJob.Stage.CLEARING`). Builders only start placing once it finishes.

## What is cleared

The area is a box. It spans the footprint plus `CLEAR_MARGIN` = 2 blocks on every horizontal side, from the build's base layer up to `CLEAR_ABOVE` = 2 blocks over its top (capped at build height). The ground below the base layer is never dug.

Inside that box, `needsClearing` selects every block that is not air and not replaceable. Grass, vines and snow layers are replaceable and stay, because placement replaces them. Fluids also stay. So does any block that is already the block the schematic wants in that cell, or in the second cell of a door or bed; its orientation is fixed during the build if needed. The job's own scaffolding is never selected.

Blocks the explicit-dig safety policy protects are left in place and counted as kept: chests and other containers, beds, spawners, bedrock, beacons and similar.

## How it runs

- **Survey without lag.** `scanSlice` checks at most 4,096 cells per server tick, walking the box from the top layer down (`scanCell`). A large area cannot stall the server, and the highest blocks are queued first.
- **Shared queue.** Clearing uses the same claim, retry and leftover machinery as scaffold removal (`claimRemoval`). A builder claims the highest remaining block, nearest first. It never breaks a block in a column another builder stands on, and it digs its own column down first.
- **Top-down order.** Breaking from the top means falling blocks such as sand and gravel never drop into already-cleared space, and builders end up digging down rather than stranding themselves.
- **Same builder behaviour as the rest of the build.** Builders use the same stand finder, reserved pillar columns, crowd-aware routes and stall watchdogs. Any temporary pillar they need to reach tall trees is recorded and removed at the end.
- **Give up cleanly.** A block that cannot be broken after six attempts is left in place and reported.
- **Announcements.** The job reports when the survey finishes ("clearing N blocks … K protected blocks left in place") and when the site is clear and construction starts. `/bot schematic status` shows the clearing progress.

## Inventory

Cleared terrain fills inventories with logs, stone, dirt and saplings. When a builder needs room for a schematic material or for scaffold, it now drops any picked-up block item the build does not need, keeping up to 128 scaffold blocks. Tools, food, armour and needed materials are never dropped. The scaffold top-up also frees space and retries before giving up.

## Tests

`SchematicBuildTest.clearingSurveysEveryCellOfTheAreaFromTheTopLayerDown` checks that the survey visits every cell of the box exactly once, layer by layer from the top.
