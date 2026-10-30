# 1.21.0 — Coordinated builders, chained pillars, ring-ordered roofs

## Pathfinding: whole pillars in one search

The A* search runs against the world as it is now, so it could plan only one pillar step per search. The block it would place under the companion did not exist yet for the next step. Tall climbs re-searched after every block, followed partial routes, and often went somewhere else instead.

`BoundedAStar` now passes each expansion the edge that reached the cell. `WorldNeighbors.edges(from, via)` treats the block a `PILLAR_UP` step places under `from` as solid footing. As a result, one search can plan a complete pillar followed by the step off it onto a wall or roof. Execution was already sequential, so each placed block lets the next step proceed with no re-search. Callers that pass a plain `Function` still use the old one-argument form.

## Coordination

- **Reserved columns.** `SchematicJob.reserveStand` reserves the stand, the head space above it and every replaceable cell below it down to footing, which is the pillar the companion will raise. `standTaken` checks this whole set, so two builders never pick the same spot or pillar in the same column.
- **Crowd-aware routes.** `ConstructionPolicy.crowdCost` adds route cost for entering or placing into another builder's reserved cells (30) or body cells (25). Routes and pillars go around other builders when there is any alternative. When there is none, the route still passes through.
- **Spread claims.** A block within 2 blocks horizontally and 3 vertically of another builder's claimed block or body costs +150, on top of the strip and distance preference. Builders work apart instead of in a cluster.

## Ring-ordered layers (roofs)

`BuildSchedule` gives every structure block a ring number in its layer. Ring 0 is every block resting on a schematic block below it (all blocks in the lowest layer). Each further ring touches the previous ring horizontally. A layer offers only its lowest unfinished ring. A roof therefore goes on as the ring over the walls first, then inward ring by ring, with eaves at the same pace outward. A lintel over a doorway waits until the wall beside it is in. Blocks no ring reaches come after all rings and get a support scaffold.

On the sample files: 03_storage_house's roof goes 48 → 40 → 32 → 24 → 16 → 5, and 16_large_warehouse's goes through nine rings from 80 down to 9.

## Scaffolding

- Scaffold removal skips any block in a column another builder stands on. A builder standing on scaffolding claims its own column first and digs it down.
- During building, a target cell occupied by scaffold that another builder is standing on is deferred instead of being broken under them.
- **Stranded builders.** On a second consecutive 10 s stall, a builder may break out through finished blocks (rebuilt afterwards). If it has at least 6 hearts, it also accepts drops of up to 6 blocks (`ConstructionPolicy.maxFall`, normally 3) until it makes progress again.

## Tests

`SchematicBuildTest` adds tests for:
- roof rings from the walls inward,
- ring counts with eaves (corners come one ring later),
- a lintel waiting for its wall,
- a six-block pillar planned in one search, compared with one step before.

All 20 sample files plan and schedule without errors.
