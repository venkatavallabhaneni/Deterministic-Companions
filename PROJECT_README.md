# Deterministic Companions â€” Minecraft Java 26.2 / Fabric

Automation for up to **four ordinary companions plus one flying hunter**, all real server players. No ML, LLMs, external decision services, Baritone dependency. A guard-selection stick is issued on request; diamond swords are issued to ordinary bots on spawn and when missing during guard/protect duty. Mining tools and construction blocks are supplied through deterministic give-style inventory grants; bots do not craft. Rectangular guards receive 64 starter dirt once per saved companion. All placement consumes inventory. Install the mod on the client as well to use the flying target stick's R key; other commands work with an unmodified client.

## Build and run

Install a **JDK 25** and set `JAVA_HOME` to it. The checked-in wrapper downloads Gradle 9.5.1.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runServer --args=nogui
```

On Linux/macOS: `chmod +x gradlew`, then `./gradlew clean build` and `./gradlew runServer --args=nogui`.

On the development machine used for this project, the exact working build command is:

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:\Users\venka\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2' clean build
```

The equivalent server command is:

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:\Users\venka\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2' runServer --args=nogui
```

The development server uses `run/`. On first launch Minecraft creates `run/eula.txt`. Read the linked Minecraft EULA and set `eula=true` there only if you accept it, then rerun the server command. The project does not accept it automatically.

Output: `build/libs/deterministic-companions-1.24.0.jar`. The `-sources.jar` is source code, not the installable mod.

`./gradlew sourceZip` builds/tests and packages the complete project, wrapper and both JARs into `build/distributions/deterministic-companions-1.24.0-project.zip`. [SOURCE_TREE.txt](SOURCE_TREE.txt) lists the delivered source tree.

## Install

1. Create a Minecraft **26.2** Fabric server using Fabric Loader **0.19.3 or later** and Java **25**.
2. Put `deterministic-companions-1.24.0.jar` and Fabric API **0.156.0+26.2** in that server's `mods/` directory. To use R targeting, install the same mod JAR and Fabric API on your Fabric 26.2 client.
3. Start the server normally. An operator with gamemaster command permission can use `/bot`.
4. Spawn a companion and supply equipment through normal dropped items or explicit vanilla operator commands. `/bot give` transfers items **out of the bot's existing inventory**.

The mod also loads in an integrated Fabric server. It performs all actions on the server thread. This integrated-server use has not been tested in a graphical client.

## Commands

See [COMMANDS.md](COMMANDS.md) for the complete command reference. Start with:

```text
/bot spawn bob
/bot spawn falcon flyer
/bot falcon give_stick
/bot falcon get_stick
/bot follow bob
/bot return bob
/bot goto bob 100 64 -200
/bot mine bob minecraft:iron_ore 32
/bot build bob bridge 20
/bot status bob
```

`return` cancels the existing task, tracks the issuing player's changing location, and completes on arrival. `come` does the same once. `follow` and `protect` persist. `home` is the separate command for the position recorded by `sethome`.

## Implementation

- Genuine `ServerPlayer` subclass with deterministic UUID, vanilla inventory, armor, health, hunger, item pickup, visibility, physics, fall damage, and movement exhaustion. An in-process connection discards outgoing packets addressed to the fake client; vanilla entity tracking broadcasts the player to real clients.
- Central `MAX_ACTIVE = 4` in `config/CompanionConfig.java`. Despawned records do not consume a slot.
- One separate flying-hunter slot (`MAX_FLYERS = 1`): `/bot spawn <name> flyer` equips an unbreakable elytra, Wind Burst II mace and a 64-rocket stack that refills after use. Automatic hunting chooses visible hostile monsters within 30 blocks with open sky above them; its flight task uses normal firework propulsion and vanilla mace hits and patrols around its owner when no threat is present. Flight plans bounded 3D air routes around mountains and checks projected motion for collisions. For mobs beside walls, it selects a supported attack spot with a clear line of sight, routes to that side, and descends there; short-range searches use finer two-block cells when needed. `/bot hunt <name>` restarts flight duty after an explicit task or stop. A player can designate any visible mob, including passive or neutral mobs, using the R-key stick. Type and inventory persist; ordinary and flying slots are counted separately. Flight needs loaded terrain and enough clear space for a player-sized entity; sealed targets may still be unreachable.
- One primary task per bot. Survival has explicit priority for a tick; tasks and navigation are not independently scheduled competing loops.
- Incremental custom A*: maximum radius 32, 2,048 expanded nodes per search, 96 search iterations per bot per tick. Searches use only loaded chunks on the server thread. Routes retain movement, jump, step, fall, swim, climb, door, break, bridge and pillar actions. Replanning handles changed targets, unsupported blocks, collision and stalled movement.
- Vanilla breaking START/STOP/ABORT actions preserve time, tools, drops, durability, enchantments and Fabric's game-mode hooks. Mining receives the minimum working tool tier through a give-style inventory grant before scanning a loaded XYZ volume extending 150 blocks in each direction from the command starting position. It checks at most 1,024 cells and eight chunk sections per tick, skips irrelevant section palettes, holds at most 32 candidates, reserves exact targets and mining work positions at least five blocks apart and prioritizes connected vein blocks. Navigation breaking shares exclusive block locks. Bots wait and rescan when other miners occupy the available work zones. After completion or a bounded failure it returns to continuously follow the requesting player. Progress counts collected normal ore drops or Silk Touch blocks rather than blocks merely removed.
- Automatic tool acquisition selects the minimum working tier from loaded harvest rules and gives it directly; no recipe, crafting table or crafting command is used. Existing usable better tools are reused. Construction plans and navigation gaps receive common cobblestone through the same give-style grant when fewer than eight safe building blocks remain, subject to inventory capacity and safety policy.
- Both `/bot <command> all ...` and `/bot all <command> ...` work for companion commands. Sprinting defaults on for ordinary walking routes, subject to normal hunger and item-use restrictions.
- Vanilla `useItemOn` construction with reach, line-of-sight, world-border/build-height and spawn-protection checks. A conservative inventory block selector powers automatic bridging and pillaring. Plans support walls, floors, bridges, pillars and solid-block stairs.
- Rectangular guard areas: `/bot guardarea 29 1 0 19 10 0 2` assigns two active companions; `/bot guard bob area 29 1 0 19 10 0` assigns Bob. Corners are inclusive and normalized. Assignments replace current tasks, attack eligible Minecraft MONSTER-category entities inside the area, and remain active until stopped or replaced. `/bot stop all` cancels them. The active companion cap remains four.
- Configurable health: `/bot health bob 40` or `/bot config all maxHealth 40` sets maximum health and heals to it; 2 points = 1 heart, allowed range 1?1,024. Maximum health persists.
- Diamond swords are supplied/reused on spawn and equipped throughout guard/protect duty. Missing/broken duty swords are reissued; full inventories drop the displaced stack normally. Eating during duty uses the offhand so the sword stays in the main hand. Normal sword durability and combat cooldown still apply.
- Melee attack cooldown, reach and line-of-sight; weapon and armor selection; manually equipped offhand shields. Protect follows a player using distinct formation positions for every active companion. Combat uses separate approach angles and a four-second no-progress watchdog that rotates a blocked route. Guard records a location. Threat ordering includes recent melee attackers, projectile owners, targeting mobs and nearby hostiles.
- Protect and rectangular guard duty split ranged duty deterministically: exactly the first half of an area assignment (one of two, two of four) uses a real Power V Infinity bow, while the remainder use the required diamond sword. Every duty bot receives the bow and one required arrow automatically; only the assigned ranged half equips and fires it. Full-draw shots lead moving ranged hostiles using their velocity and vanilla arrow gravity; bow release still uses the server's normal projectile and damage rules. Door, lever and button route edges use an empty hand and vanilla interaction hooks. A stalled water edge is abandoned and temporarily blocked so A* can select a dry alternate route.
- Automatic food consumption, armor selection, drowning response, fire/water seeking, lava escape attempts and low-health retreat. Companions remain vulnerable to normal damage and can die.
- Vanilla playerdata persists inventory, equipment, health and hunger. Separate versioned JSON persists owner, identity, home, configuration and passive mode. Metadata uses temporary files, replacement and a previous-file backup. `remove` saves/despawns; it does not delete data. Respawn by name reloads the same identity and inventory.

## Safety defaults

`breakBlocks=true`, `placeBlocks=true`, `bridge=true`, `sprint=true`, `pvp=false`, `friendlyFire=false`, `maxDistance=256`.

Automated construction only selects common solid materials from a whitelist. It excludes falling blocks, valuables, containers and functional blocks. Explicit `/bot place` can use the requested block if present. Breaking conservatively protects all block entities, valuable blocks and listed functional blocks, including empty containers; this is stricter than protecting only occupied containers. Explicit mine/dig/break requests may harvest anvils and ancient debris with the correct tool, acquiring it when needed for mining; automatic navigation still protects them. It also refuses to expose adjacent lava or remove the bot's floor without a nearby safe landing.

Owners and companions are never intentional combat targets. Protected players are excluded during protection, including from splash damage. Tamed animals and allied entities are excluded with friendly fire off. PVP requires both bot configuration and ordinary server rules to permit it. All `/bot` commands require gamemaster permission; this is an operator tool, not a per-owner untrusted-player permission system.

## Verification

`clean build` compiles the full source and runs the automated tests, including pure logic, the production command parser, mining-to-follow transitions, resource reservations, bounded target retries, direct tool/block supply, item components and harvest tags. These tests do not launch a world. See [TESTING.md](TESTING.md) for test coverage and world-level scenarios.

The dedicated-server launch was exercised: Fabric loaded Minecraft 26.2, the mod, and the pinned Fabric API. It stopped at the Minecraft EULA gate (`eula=false`). No world-level or graphical multiplayer tests have been completed in this session. Compilation and passing logic tests do not establish that every world interaction is correct.

## Known limits

- World-level area guarding, sword enforcement, configurable health, fake-player movement, combat and construction behavior still needs the integration tests in `TESTING.md`. Treat the release as an experimental implementation pending those tests.
- Navigation is deliberately local and conservative, not a complete Baritone replacement. It does not route through unloaded terrain, portals, boats, minecarts, elytra routes, parkour jumps or every custom collision shape. Complex doors, water currents, ladders and multi-bot congestion may produce a bounded failure instead of reaching the goal.
- Guard areas span at most 128 blocks per axis and only monitor loaded entities in their assigned dimension. Guards need safe reachable posts/targets. Navigation and emergency survival movement can leave the area; the area bounds target selection, not every movement step. Assignments/tasks do not persist across restarts. Guards assigned to another rectangular area are excluded from automatic selection until stopped; repeating the same area with a new count resizes that assignment.
- Mining reserves five-block work zones, but travel through narrow corridors and item pickup do not have team traffic reservations. Separate work zones can serialize mining of a small vein. There is no cooperative inventory sharing.
- Building supports at most 4,096 placements and dimensions at most 64. All initial plan chunks must already be loaded. Plans stop with status when materials, permission, supports or a route are unavailable. Navigation may spend extra building materials beyond a plan's initial placement estimate.
- `/bot build stairs` creates a solid staircase; it does not craft or orient stair items. `/bot place` uses a default block item interaction; it does not accept arbitrary block-state properties or NBT. Required blocks are supplied as common cobblestone when the bot has no safe building material.
- There is no `/bot craft` command and no automatic recipe, furnace or workstation workflow. Tools and construction blocks are supplied directly through give-style inventory grants. Shields need to be equipped explicitly.
- Mining has built-in progress mappings for standard ores and stone, and supports Silk Touch ore pickup. Unusual blocks and modded loot tables can yield items outside those mappings; such tasks can stop short of the requested count. Inventory progress is net collected inventory, not an item-provenance ledger.
- Return/home do not teleport across dimensions. Follow/protect wait when their player is offline or in another dimension. Low-health retreat yields control back to persistent tasks after damage is no longer recent. Bots are saved as inactive across restarts and must be spawned again; active tasks/plans are not resumed.
- Death uses normal drops and keep-inventory rules. Spawning a dead saved companion starts a new life with normal health/hunger and the inventory remaining after death. It does not recover dropped equipment.
- Skins use the deterministic profile's default appearance. No skin/profile download service is used by this mod.

Exact verified toolchain and API notes: [docs/TOOLCHAIN.md](docs/TOOLCHAIN.md).

Vegetation traversal fix: [1.2.1 verification notes](docs/FIX-1.2.1.md). Navigation and interaction approach checks use physical collision; harmless grass, tall grass, flowers and similar non-solid blocks need not be cleared.

## Guard selection wand (1.3.0)

Run `/bot guardarea all` or `/bot guardarea bob,tracy` to receive and hold a selection stick. Left-click a block for corner 1, then right-click another block for corner 2. The second click immediately assigns the chosen companions. A particle outline shows the inclusive XYZ cuboid to you while the wand is held. Select an upper corner high enough to include the mobs; clicking two floor blocks selects only a one-block-high layer. No WorldEdit or client-side mod is required for the particle outline.

Existing coordinate/count commands still work. `/bot stop all` stops the guards. Wand selections are per operator, confined to one dimension, capped at 128 blocks on each axis and reset on disconnect/restart. `all` captures the active companions in your dimension when requesting the wand. Maximum 16 simultaneous operator selections.

Navigation compares walking, safe falls, bridges, placed steps and pillars. Guards with suitable inventory blocks can build toward hostile targets or unsupported posts, using normal placement and consuming those blocks. `bridge` and `placeBlocks` must be on (both default on); permissions, loaded chunks, hazards and bounded search limits still apply. Block placement is not guaranteed when no safe route/attachment exists. Failed threats receive a ten-second retry delay so other targets can be considered; adding building materials clears that delay.

## Automation fixes (1.4.0)

See [release notes](docs/FIX-1.4.0.md). Rectangular guards receive **64 dirt once**, with the issued flag saved across despawn/restart. Reassigning guard duty does not refill it. Refill explicitly with `/give bob minecraft:dirt 64`. `/bot give` still transfers items out of a bot. Mining and navigation construction grants do not replenish guard building supplies.

## Direct tool supply and expanded mining and guard combat (1.8.0)

Version 1.8.0 retains the direct-supply model and removes the table-stall workflow entirely. There is no craft command, recipe execution or workstation interaction. Mining route failures trigger bounded rescans instead of the old target-cap stop; mining gives the minimum valid tool when needed and supplies one 64-cobblestone stack when bridging is enabled and fewer than eight common building blocks are present. Build plans and navigation also issue cobblestone when their inventories run low on safe construction blocks. Guard/protect duty issues Power V Infinity bows and one arrow to the ranged half of the duty group, predicts moving ranged targets, interacts with doors/levers/buttons on routes, and replans when flowing water stalls movement. See [release notes](docs/FIX-1.8.0.md).



## In-game controls (1.9.0)

Run /botui as an operator to open bot selection, actions, mining/building presets, settings, inventory, spawn/despawn controls and player selection. Uses the vanilla chest interface; no extra client mod is required. See [UI reference](docs/UI-1.9.0.md).
