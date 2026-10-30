# Vegetation traversal and interaction visibility

Minecraft 26.2 / Fabric, version 1.2.1.

Short grass, tall grass, ferns, flowers, saplings, crops, vines, torches and rails use their actual physical collision shapes for traversal. A block does not need to be air to be traversable. Solid blocks, headroom, floor support, unloaded terrain and the existing hazard policy remain checked.

Inspection found that the previous body-clearance check already used physical collision, but interaction-position selection, breaking reach/visibility and placement visibility used selectable outlines. A tall-grass outline can intercept those rays and cause a bot to choose another approach position or refuse a reachable interaction. Those checks now share a collision-based visibility helper. The test suite reproduces the old outline failure against actual Minecraft raycasting and verifies the new collision ray reaches the block beyond the grass while still rejecting a solid intervening block.

Cached route edges requiring a break now recheck physical collision instead of simply checking for non-air. If an obstacle is replaced by non-solid vegetation, navigation walks through without breaking the vegetation. Explicit mining/digging commands can still target non-solid plants.

`VegetationNavigationTest` adds 29 tests using actual Minecraft 26.2 block states and collision/raycast algorithms in a small in-memory fixture. They check 14 passable plant/decoration types, both halves of tall plants, six solid obstacles, seven hazards, headroom, and the reproduced visibility failure. The complete suite contains 185 tests. No live server world or graphical client was used for these tests; ordinary follow/goto grass avoidance was not independently reproduced in a running world.

Manual check: place short grass, both halves of tall grass, ferns and flowers along a flat route. Run follow/goto through them with breaking disabled, then mine/build behind vegetation. The bot should neither detour around nor break harmless vegetation solely to reach a target. Repeat with a stone wall, fence, cactus, berry bush and fire: physical obstructions and hazard avoidance must remain effective.

API behavior was checked in the downloaded 26.2 `CollisionGetter`, `BlockCollisions`, `ClipContext`, and vegetation block sources. Fabric's [collision-shape documentation](https://docs.fabricmc.net/develop/data-generation/block-models) also distinguishes outline shapes from collision shapes.
