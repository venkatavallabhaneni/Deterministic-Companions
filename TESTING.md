# Verification and Minecraft integration scenarios

Current release behavior: crafting has been removed. There is no `/bot craft` command, recipe execution, furnace workflow or crafting-table preparation. Any older crafting scenarios below are historical and are superseded by direct give-style tool and block supply checks.

## Automated logic tests

Run `./gradlew clean build` (Windows: `.\gradlew.bat clean build`) with JDK 25.

The original 17 pure-logic JUnit tests are in `src/test/java/dev/companions/CompanionLogicTest.java`. They run without starting Minecraft. They cover:

- Four active companion limit and capacity after a slot is released.
- Task start/tick/complete lifecycle, cancellation and replacement order.
- Return tracking a moving player, canceling mining, completing on arrival and ending when the target disappears.
- Follow remaining active at arrival and following subsequent movement.
- Path action cost ordering, deterministic cheaper-route choice, radius limits, incremental and total search budgets.
- All five build-plan geometries, size validation and immutable independent plans.
- Inventory bridge material selection and exclusion of valuable/empty candidates.
- Tool correctness, speed, enchantment and durability scoring.
- Threat priority ordering and central block safety.

The full suite passes with zero failures, zero errors and zero skipped. Coverage includes production command parsing, item components, harvest tags, equipment stowing, bounded target retries and direct mining/construction supply. The command/data tests bootstrap vanilla registries but do not create or start a server world. Gradle's HTML report is `build/reports/tests/test/index.html`.

## Validation performed in this workspace

- The full mod compiled repeatedly against the actual Minecraft 26.2 jars.
- `build` produced the installable mod JAR and sources JAR.
- The Fabric dedicated-server launcher loaded Minecraft, Fabric API and the mod, then stopped at `eula=false`.
- The scenarios below have **not** been run in a world. EULA acceptance and a connected Minecraft client are needed to finish that validation.

## Flying hunter (1.14.0)

1. Spawn four normal bots, then `/bot spawn falcon flyer`. Verify `/bot list` reports 4/4 ordinary and 1/1 flying, and that both an extra ordinary spawn and an extra flyer spawn fail. Remove the flyer; verify its slot is free and normal bot capacity remains four.
2. Check Falcon's inventory and chest slot: the elytra carries the unbreakable component; the mace has Wind Burst II; rockets start at 64 and refill within one second after being used. Verify no other equipment or inventory is silently deleted if saved inventory was full.
3. In open loaded terrain, spawn a zombie within 30 blocks. Watch the flyer take off by jumping, use a real rocket while gliding, approach above the mob, then perform a normal mace hit or smash within player reach. Verify it never attacks players, pets or other companions. Move the zombie outside 30 blocks and verify the flyer resumes owner patrol.
4. Test under a low roof, over water and near unloaded chunks. It should wait or use ground navigation instead of entering solid or unloaded space. Test `/bot stop falcon`, `/bot hunt falcon`, remove, server restart and respawn; the saved role and inventory must survive.
5. Test four ordinary bots mining/building/guarding while the flyer hunts. Verify independent tasks and responsive server ticks. Explicit commands that replace the flight task intentionally suspend hunting until `/bot hunt falcon`.

Live flight and combat checks require an EULA-accepted test server and a connected client; automated tests do not measure hit rate or guarantee flight in every terrain shape.

## Dedicated-server setup

Use a separate test world. Launch `runServer --args=nogui` through Gradle. If you accept Minecraft's EULA, set `run/eula.txt` to `eula=true` and relaunch. Connect a Minecraft 26.2 client and grant yourself operator permission using the server console. For predictable tests set `spawn-protection=0` in the test server's properties, or test outside the protected spawn area. Companions do not bypass spawn protection.

Use a flat, well-lit region with known coordinates. The operator may deliberately create fixtures and supply test items with vanilla commands. Placement tests must verify actual inventory consumption. Account for the explicitly issued sword, selection wand, directly issued mining tools, automatic construction cobblestone and one-time guard starter dirt.

## Player lifecycle and persistence

1. Spawn `bob`, `alice`, `steve`, `alex`. Confirm all appear as players in the world/tab list, with independent inventories and visible held items.
2. Spawn `fifth`. Expect exactly `Maximum companions reached: 4/4` and no extra entity or data loss.
3. Supply Bob with a damaged pickaxe, food, armor and 64 cobblestone. Record inventory, health and hunger. Set home and a nondefault configuration.
4. Remove Bob. Verify normal player despawn, active count 3, and that playerdata and mod metadata remain on disk. Spawn Bob again and verify inventory/equipment/home/configuration. Verify the free slot can instead be occupied by another named bot.
5. Save and restart the server normally. Spawn Bob by name; verify the same UUID and retained data. Tasks should be idle, not resumed.
6. Kill a bot with normal damage with keep-inventory both off and on. Verify normal drops/rules and that spawning a new life does not duplicate gear. Check that ordinary falls, drowning, fire and starvation can hurt bots.

## Commands and primary-task semantics

1. Start Bob mining. Issue `return bob` and move while Bob travels. Confirm mining is canceled and Bob follows your updated location, then completes once near you.
2. Move away after completion: Bob must not keep following. Repeat using `come`.
3. Issue `follow bob`, move, stop, then move again. Bob must remain following until replaced.
4. Set a home, move elsewhere, issue `home bob`. Verify home is distinct from `return bob`.
5. Test explicit target players, relative coordinates, stay/stop, player disconnect, and another dimension. Return must end on unavailable target; follow/protect should wait. Test maxDistance failures.
6. Test every command in `COMMANDS.md`, including invalid names/IDs, missing inventory, invalid dimensions and permissions.

## Navigation, doors, water, climbing and failure handling

1. Test flat walks, long segments spanning more than one search radius, sprinting, a one-block step, bottom slabs, stairs, two-block headroom and three-block descents.
2. Test a wooden door, an iron door, a ladder, shallow water and a water ascent. Observe status and confirm no instant teleports or wall penetration.
3. Introduce an obstacle after a path is computed. Remove a floor or bridge block. Move the player target. Verify route invalidation and replanning.
4. Create a sealed, protected obstacle. Verify jump retry and bounded failure after repeated stuck detection, instead of infinite forward movement.
5. Put lava, fire, cactus, powder snow and a deep drop beside a route. Verify route choices and survival outcomes. Complex cases may fail conservatively; record failures rather than assuming support.

## Breaking, tool selection and mining

1. Place an exposed iron vein outside spawn protection. Give Bob an appropriate pickaxe. Mine a requested count and verify crack timing, sounds, tool damage, drops, vein continuation and collected-item progress.
2. Repeat without a suitable pickaxe; the bot should not invent one. Compare dirt/shovel, logs/axe and stone/pickaxe selection.
3. Repeat with Fortune, Silk Touch and Efficiency. Count the real items collected and inspect tool durability.
4. Cancel during breaking, change the target block mid-task, fill the inventory and remove a target. Verify bounded completion/failure and no stale block-breaking state.
5. Put containers, furnaces, beacons, spawners and valuable blocks in the way. Verify the safety policy leaves them intact. Confirm spawn protection and another mod's interaction denial remain effective.

## Construction, bridging and pillaring

1. Supply exactly 20 cobblestone and request a bridge of length 20 over a gap with a valid initial anchor. Verify each block appears via placement and inventory drops to zero; Bob must walk on supported positions.
2. Repeat with insufficient blocks, empty inventory and inventory containing only diamonds/containers/functional blocks. No free blocks should appear.
3. Remove a placed bridge block while Bob is approaching. Verify stopping/replanning instead of walking into the gap.
4. Request a pillar under an open sky, then beneath a low ceiling. Check jump/place timing, inventory consumption, fall damage and collision.
5. Build a wall, floor and solid staircase. Check captured bot orientation, exact coordinates, materials and plan progress. Test occupied/protected cells and unreachable placements.
6. Test explicit placement of a named inventory block. Test `placeBlocks=off` and automatic `bridge=off` separately.

## Combat, protect, guard and survival

1. Compare attack timing and reach with an ordinary player. Verify line-of-sight, real weapons, armor, cooldown and shield use after explicitly equipping a shield.
2. Protect a moving player with one to four bots. Arrange melee and projectile attacks, a mob targeting that player and a nearby hostile. Verify distinct formation and combat approach positions, engagement priority, four-second blocked-route recovery, and return to formation after combat. Leave a bot at low health after its attacker becomes stale and verify Protect resumes instead of repeatedly recreating escape tasks.
3. Guard a position with radius 12. Lure a hostile outside the area; the bot should stop pursuit and return.
4. Test owner, other companions, the protected player, friendly pets and team members near a sweeping attack. They must remain protected according to configuration, including collateral damage.
5. Test PVP off, PVP on with server PVP off, and both enabled. Test passive and flee.
6. Lower hunger with food available. Verify real use duration, food consumption and hunger restoration. Test low air, fire near water, lava near shore and low-health retreat; no survival action should grant invulnerability.

## Four-bot concurrency and performance

Run Bob protecting, Alice mining, Steve bridging and Alex following simultaneously. Inspect their separate status, inventory, paths, homes and configurations. Stop/return one; the other three must continue their own tasks.

Profile tick time with vanilla `/debug start` and `/debug stop` before and during activity. Include four simultaneous unreachable searches and world edits. Verify the 96-iteration per-bot search budget and 2,048-node limit; confirm there are no worker-thread world reads or unbounded search loops. Heavy entity farms and unusual collision shapes require separate load testing; no benchmark claims have been made from unit-test timing.

## Crafting, tool acquisition, group mining and sprinting (1.1.0)

These world scenarios remain manual and have not been claimed as passed.

1. Start Bob with an empty inventory in a safe loaded area with reachable logs and exposed stone. Run `/bot mine bob minecraft:stone 16`. Verify he breaks logs, picks them up, crafts planks/sticks/table/pickaxe, places the table, then mines stone. Count materials before/after; confirm real durability and drops.
2. Repeat for iron ore with no tools: Bob must acquire a wooden pickaxe, mine cobblestone, craft a stone pickaxe, and only then harvest iron. For diamond ore, also supply reachable iron ore: verify a crafted/placed furnace, actual raw iron/fuel use and normal smelting time before an iron pickaxe.
3. With iron equipment and reachable diamonds, order ancient debris mining in the same dimension. Confirm a diamond pickaxe is crafted before breaking debris. A bot in the Nether without required Overworld resources must report a bounded supply failure, not create diamonds or teleport.
4. Give an existing usable diamond pickaxe and repeat stone/iron mining. Confirm it is reused. Give an almost broken tool, consume its remaining usable durability, and confirm reacquisition without item duplication.
5. Test every standard pickaxe tier, axes on logs and shovels on dirt. Change recipes/tags with a datapack and `/reload`: crafting must honor the current recipes, and harvest selection the current tags.
6. Place targets 33, 63, 64 and 65 blocks from the mining start. The first three should be found in loaded chunks; 65 must be outside the search. Remove support or unload a target chunk during travel and verify bounded replanning/failure.
7. Spawn four bots with enough independently reachable resources. Run `/bot all mine minecraft:stone 16` and `/bot mine all minecraft:iron_ore 4`. Verify separate targets, inventories, per-bot progress and no shared task cancellation. Normal pickup is still vanilla; neighboring players/bots can collect each other's drops.
8. Move while bots finish mining. Each should return to your changing position and follow continuously afterward. Issue `/bot stop bob` during gathering, crafting, furnace waiting and the follow phase; verify no stale mining resumes or completion message fires.
9. Test `/bot craft bob minecraft:stick 16` and pickaxes with exact ingredients and one ingredient missing. No duplication, no half-consumed ingredients on failure. Test inventory overflow and a bucket-remainder recipe such as cake; unused/remainder inputs must be returned or dropped normally.
10. Stop/despawn/restart while iron is smelting. Verify placed furnace and its contents remain in the world and persisted inventory is correct. Existing unrelated furnaces/containers must never be looted.
11. Test both `all` syntaxes for inventory, equip, config, build, give, drop, remove, guard and follow. Make one bot lack materials: other bots should still receive their commands. Maximum active count remains four.
12. Compare `/bot sprint bob off` against `on` on a straight safe route. Verify speed changes, normal exhaustion, no sprint at food 6 or below, and no sprint while eating/sneaking or performing bridge placements.

## Area guarding, health and issued swords (1.2.0)

Automated additions: 19 production command parsing checks plus seven tests covering cuboid normalization/boundaries, bounded area size, exact guard counts, health validation and old metadata, real ItemStack sword reuse/overflow, and task replacement/cancellation. These do not start a server world.

Manual world checks (not yet executed):

1. Spawn four bots and verify each has one diamond sword. Despawn/respawn with a damaged sword: it must be reused, not duplicated or repaired. With a full inventory and no sword, verify the displaced selected stack drops rather than disappearing.
2. Put two bots on follow/protect and issue `/bot guardarea 29 1 0 19 10 0 2`. Verify exactly two switch duties, including cancellation of old protect state. Test both corner orders and a one-block-wide region. Use appropriate terrain/build height for the fixture.
3. Place zombies, skeletons, slimes and flying hostiles at both boundary blocks, just outside, and inside more than six blocks from the center. Guards should target eligible monsters throughout the region, drop outside targets, respect normal reach/cooldown and return to posts. Verify owners, protected players, companions and pets are safe.
4. Assign remaining companions to another area. Resize the first assignment to one, then request an unavailable count; confirm the second area remains unchanged and failure leaves existing tasks intact. Confirm total active bots never exceeds four.
5. Run `/bot stop all` and `/bot all stop` during approach and combat. Verify all explicit guard/follow/protect tasks stop and old tasks do not resume. Ordinary survival/self-defense remains enabled.
6. Give guards netherite weapons, food and shields; verify diamond main-hand enforcement during idle/combat, eating in the offhand, normal durability, and replacement of a missing/broken sword. Check inventory conservation of non-issued items.
7. Set health to 40 and 100 using both command forms and `all`. Confirm current/max health, real damage and death, then despawn/restart and confirm max health and saved current health. Load old metadata without maxHealth and verify default 20.
8. Unload target chunks, remove guard-post support, move a guard across dimensions and test blocked routes/low-health retreat. Verify bounded retries, safe status reporting, no forced chunk generation and no chasing stale targets.

## Vegetation traversal (1.2.1)

29 additional tests exercise Minecraft collision and raycast code against block fixtures. See [the regression details and manual scenarios](docs/FIX-1.2.1.md). The old outline ray obstruction is reproduced; live-world follow/goto behavior has not been independently reproduced.

## Selection wand and guard construction (1.3.0)

15 additional automated tests cover native comma-list parsing and coordinate-command compatibility, name validation, independent XYZ selections, invalid/oversized/different-dimension corners, idempotent right-click completion, bounded twelve-edge particle geometry, wand item tagging, bridge choices over safe drops, placed-step/pillar prerequisites, and incremental A* pillaring with consumed material budgets. Production callbacks compile against the pinned Fabric API; its server-side interaction mixin was inspected to confirm cancellation of block breaking/use. These are not live-world tests.

Manual checks not yet run:

1. With a vanilla 26.2 client connected to the Fabric server, request `/bot guardarea bob,tracy`. Verify the supplied stick is selected, left-click selects without breaking in survival/creative, right-click selects without opening containers, and only Bob/Tracy switch tasks after the second corner.
2. Select corners differing on X, Y and Z, reverse their order, and select a one-block-wide region. Confirm both corners and all twelve cuboid edges are shown by particles to the selecting player. Use appropriate particle settings/view distance.
3. Have two operators select different areas simultaneously. Verify independent corners and targets. Test ordinary sticks, renamed sticks, non-operator use, cross-dimension clicks, excessive size, a bot removed between clicks, and repeated held right-click. No repeated assignment/chat spam or unintended block mutation.
4. Verify old numeric/count commands still assign immediately. Test `/bot stop all` during selection and during guarding: pending selection alone must not start guards until the second click, and active tasks stop. Disconnect/restart and verify stale wand selections cannot assign bots until the command is run again.
5. Put hostiles across a gap with a safe floor two or three blocks below, on a raised platform, and above an unsupported guard post. Supply cobblestone/dirt and verify bridge, step and pillar placements consume inventory and permit approach/normal melee attacks. Repeat with zero blocks, valuable blocks only, placement disabled, protected terrain and missing supports: no free blocks or unsafe forced placement.
6. Remove a placed block during approach, exhaust materials, and move the hostile. Verify bounded replanning, no endless jump-without-placement loop, and that giving blocks allows retry. A blocked closest hostile should not prevent attacking other reachable threats.

## Automation regressions (1.4.0)

The 1.4.0 material-grant scenarios supersede older expectations that missing crafting inputs must be gathered or reported as absent. Automated coverage includes 28 cases for held-item restoration (including a throwing hook and a full inventory), actual vanilla recipe deficits, overlapping ingredient matching, atomic inventory grants, 100-tick check cadence, persistent starter dirt, five-block mining zones, cross-task exclusive breaking and bridge approach geometry. These tests do not run a Minecraft world.

Manual scenarios still to run:

1. Spawn Bob with his diamond sword, give a stack of logs/dirt, then request sticks, crafting tables and wooden/stone/iron/diamond pickaxes while holding each item. Test both a nearby table and a table that must be crafted/placed. Verify the menu opens, held items survive, ingredients are consumed, and the crafting output is correct. Test full inventories and occupied crafting grids.
2. Empty the required ingredients, request multiple batches, and observe inventory every tick: at most one missing-input grant per 100 ticks, no ground-item spam, no repeated complete-recipe grants. Existing ingredients must reduce the grant. Remove ingredients mid-task and verify the next check replaces the shortage.
3. Give four bots building blocks, put a large exposed stone field within 64 blocks, and use `/bot mine all minecraft:stone 32`. Verify distinct exact targets and mining positions at least five blocks apart. Repeat on a small ore vein: bots should serialize/wait rather than break the same block. Stop/remove one bot during mining and verify its reservations release. Include supply gathering and navigation obstacle breaking.
4. Put usable dirt/cobblestone only in inventory slots 9-35, with the sword selected. Test a bridge gap, steps and repeated upward pillars while following, mining and guarding. Verify face retries never swap the sword back into the placement attempt, blocks decrement only on successful placement, and bridge approach stays supported.
5. Mine an elevated block with no existing supported interaction position but a constructible route. Check that building is considered and the bot reaches mining range. Repeat with no materials, blocked placement, protected blocks, other entities occupying placement space, and missing bridge support.
6. Use `/bot guardarea bob,tracy`, select an XYZ volume and confirm each receives exactly 64 dirt once. Exhaust it and reissue guardarea, remove/spawn the bot and restart: none may automatically refill a previously issued supply. `/give bob minecraft:dirt 64` must allow construction retries. Test a full inventory on the initial assignment, then make space.
7. Place hostiles behind an obstacle and across/above gaps inside the area. Verify line-of-sight approach, normal reach/cooldown and sword restoration after placement. Change the target and remove support during construction. Verify failed edges are temporarily excluded and paths recompute after refilling materials.
8. Interrupt mining/crafting/follow with eating, lava escape and self-defense, then verify the resumed task does not continue toward the old emergency destination. Verify protect/guard control their own threats and `/bot stop all` cancels the active tasks.
9. Profile all four bots with large loaded block fields and unreachable targets. Confirm bounded searches, bounded placement retries, five-second material checks and no unsafe world reads from worker threads.

## Direct tool supply and 150-block XYZ scan (1.7.0)

The current regression covers direct give-style tool supply, automatic construction cobblestone, bounded target retries and the expanded scan volume. No crafting table, recipe, furnace or background material check is involved.

Manual tests still to run in a Minecraft world:

1. Spawn four bots with empty inventories and run `/bot mine all minecraft:stone 32`. Verify each receives a wooden pickaxe directly, keeps its diamond sword, scans independently and mines separate targets.
2. Repeat with iron ore, diamond ore and ancient debris. Verify the minimum valid pickaxe tier is issued directly (stone, iron, diamond), with no table placement or recipe activity.
3. Request wall, floor, bridge, pillar and stairs plans with empty inventories. Verify common cobblestone is issued directly, consumed only on successful placement, and a full inventory reports failure without replacing held equipment.
4. Place target blocks at offsets 150 and 151 on X, Y and Z. Confirm 150 is discoverable and 151 is excluded. Repeat with unloaded chunks and four simultaneous no-match scans: no forced chunk generation or server stalls.
5. Confirm `/bot craft bob minecraft:stick` and `/bot all craft minecraft:stick` are rejected as unknown commands.

Compilation and these automated tests do not verify live-world interactions. See the release notes for inspected 26.2 APIs and implementation limits.

## Moving-target route reuse (1.12.0)

1. Run `/bot follow all` and `/bot protect all <player>` separately with four bots. Walk, sprint and turn around on open ground, stairs and a one-block bridge. Bots should continue along a short retained route while calculating its replacement tail, rather than stopping for every player step.
2. Move the target while a route requires placing a bridge block or breaking an obstacle. Retargeting must preserve the current action. Verify no free placement or skipped action is introduced by route reuse.
3. Remove the next bridge support or place stone across the next route cell. The live edge checks must stop movement and invalidate the route even when a repair was being calculated.
4. Test return/come completion and continuous follow separately. Stop during a route repair: neither movement nor the repair may resume until another command starts it.
5. Profile four bots with the same terrain before and after this version. Collision checks are cached only during each 96-node search slice; no performance percentage is claimed without live profiling.
## Bot UI (1.9.0)

1. With the updated server mod and an unmodified compatible client, run `/botui` as an operator. Confirm the six-row menu opens; non-operators and server console cannot open it. Revoke permission while open and confirm controls stop working.
2. Spawn two bots via Manage, select All and click Follow/Protect. Verify both commands act as the operator and preserve existing ranged allocation. Select another real player on Players, then Protect; verify the correct player is protected. Select one bot and Stop; the other keeps its task. STOP ALL cancels both.
3. Hover over bots to see live task/health/position; wait while they move to verify refresh. Despawn a selected bot from another operator's menu: its old selection must show offline rather than redirect actions.
4. Inventory safety: carry a real stack when opening; try shift-click, drag, drop, number keys, double-click and middle-click. Close/reopen and compare real player and bot inventories: no icon gains, swaps, deletions or duplication. Inventory-page copies must be equally protected. Repeat with a full inventory and two operators using independent menus.
5. Test mining presets/quantity cycling, building presets/size cycling, all configuration pairs, food/equipment, sethome/home and Go to my current block. Verify commands/errors match the ordinary `/bot` equivalent. A wall is size x 3; a floor is size x size.
6. Use the guard-area wand button, select corners, reopen the menu and stop guards. Check that closing the menu alone leaves tasks running. Test despawn confirmation and cancellation; respawn verifies saved inventory persists.
7. With more than 18 online real players, verify Players pagination and that an offline target fails without choosing a different player.
## Flying patrol and target stick (1.15.0)

1. Install the same mod and Fabric API on server and client. Spawn `/bot spawn falcon flyer`; observe eight patrol posts 14 blocks horizontally and 8 blocks above you rather than hovering at your X/Z location. Move and confirm the patrol follows the moving center.
2. Run `/bot falcon give_stick`, hold the selected stick, aim at a visible zombie or skeleton within 30 blocks and press R. Confirm Falcon chases and attacks that specific mob with its mace, then resumes patrol. Check key rebinding in Controls.
3. Try an ordinary stick, a guard wand, a mob behind a wall, a mob farther than 30 blocks, a player, and a second operator without ownership. None should assign an attack. Confirm the targeting stick remains bound to its original flyer after despawn/respawn.

## Flyer cave avoidance and smoother flight (1.16.0)

1. Spawn a hostile mob in a cave within 30 blocks of Falcon and one outdoors. Falcon should select the visible outdoor mob and must not walk into the cave for the hidden one. Move the outdoor mob under a solid roof: automatic pursuit should end when sight or open-sky access is lost.
2. Use `/bot falcon get_stick` and target a cow, villager, neutral mob, then hostile mob. Each is a valid explicit mob target if visible within 30 blocks; a player or companion is not. Manual targets may still be physically unreachable in enclosed terrain.
3. Watch Falcon circle its moving owner and turn around obstacles. Yaw changes should be bounded to 6 degrees and pitch to 4 degrees per server tick; rocket use should not fire repeatedly while already moving quickly. Compare with 1.15.0 on open terrain.

## Mountain routing and small-mob strikes (1.17.0)

1. Spawn Falcon beside a mountain or cliff. Put a visible hostile on the other side, then use the targeting stick on a chicken in open terrain near the cliff. Verify Falcon routes over or around solid terrain, does not keep flying into the wall, and lands a normal mace hit on the chicken.
2. Stand close to a steep rock face so one of Falcon's patrol posts would be inside it. Verify the patrol goal is raised into clear air and Falcon continues patrolling.
3. Repeat with an unloaded chunk, enclosed cave and impassable roof. Falcon should report a blocked air route rather than attempt unlimited searches or tunnel through stone. Check server tick responsiveness during repeated failures.

## Wall-adjacent visible targets (1.18.0)

1. Place a chicken or other mob next to a solid wall with open ground on one side. Use `/bot falcon get_stick`, aim at it and press R. Falcon should approach the clear side, descend beside the mob and strike with its mace instead of circling directly above the wall.
2. Repeat with the mob beside a corner and in a narrow approach that is wide enough for a player. Block Falcon's first approach after assignment; it should retry another candidate rather than restarting the same route every tick.
3. Verify automatic cave avoidance still applies, and manually targeted flying mobs can be struck when Falcon gets within normal player reach. Completely sealed targets remain physically unreachable.
