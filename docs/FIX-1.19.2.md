# 1.19.2 — Builders no longer loop placing dirt

Symptom (03_storage_house): after the walls and roof, builders stood still, then moved elsewhere and placed another dirt block, then repeated. One-high dirt pillars collected along the inside walls.

## Causes

1. **Crouched reach check (introduced in 1.19.1).** To centre on its stand, a builder crouched and stepped. A crouching player's eyes are about 0.35 blocks lower. On the tick centring finished, the builder judged whether it could place while still crouched. It failed, marked a good stand as bad and chose another. The next-best stand was usually one dirt block higher, so it pillared, crouched again, and the loop continued.
2. **Two reach rules disagreed.** `StandFinder` accepted a stand when a clickable face was within 4.3 blocks, with the block centre up to 5.2 blocks away. The task only tries to place with the centre within 4.5. At such stands the builder arrived and never tried.

## Fixes

- Centring nudges the builder with normal physics and never crouches. Stands are chosen for standing eye height.
- After reaching a stand, the builder waits half a second at full eye height before deciding the stand failed. Placing and breaking are retried every tick during that wait.
- `StandFinder` also requires the block centre within reach and prefers closer stands. The task attempts a placement whenever a face can be clicked, as well as when the centre is in reach.
- The height check for "at the stand" allows slabs and path blocks (up to 0.75 blocks).

`SchematicStandTest.houseRoofIsPlacedFromTheFloorWithoutPillars` covers the storage house roof from many starting positions. Every stand must be reachable from the floor and keep the centre within the task's reach. The crouching issue depends on live player poses, so unit tests cannot reproduce it; it needs an in-game check.
