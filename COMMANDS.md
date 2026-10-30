# Command reference

Every command starts with `/bot` and requires gamemaster/operator permission. Names are 1–16 letters, digits or underscores. Target player/entity arguments accept vanilla selectors. Coordinates use vanilla block-position arguments, including `~` and `^` relative forms.

## General

| Command | Behavior |
|---|---|
| `spawn <name>` | Spawn beside the issuer; restore saved identity/inventory when present. Maximum four active. |
| `spawn <name> flyer` | Spawn the one additional flying hunter with an unbreakable elytra, Wind Burst II mace and replenished 64-rocket stack. Separate 1/1 slot. |
| `remove <name>` | Cancel task, save and despawn. Preserve saved data. |
| `list` | Active count, names, tasks/progress and distance in the same dimension. |
| `status <name>` | Task, progress, health, hunger, position, remaining path and navigation status. |

`/bot hunt <name>` restarts the flying hunter's automatic 30-block hostile search after `/bot stop`, follow or another task. An ordinary bot cannot use `hunt`. The flying role is saved by name; use the same spawn form after removing a flyer.

## Movement

| Command | Behavior |
|---|---|
| `come <name>` | Come to the issuer once. |
| `return <name> [player]` | Cancel current task, track the live player and complete within approximately 2.5 blocks. Defaults to issuer. |
| `follow <name> [player]` | Continuous follow, default issuer. |
| `sprint <name> on\|off` | Enable/disable sprinting on ordinary routes; default on, needs food above 6. |
| `goto <name> <x> <y> <z>` | Navigate to block coordinates. |
| `stay <name>` | Record current position and hold it, subject to survival emergencies. |
| `stop <name>` | Cancel explicit task and navigation. Automatic survival/self-defense remains enabled. |
| `sethome <name>` | Record bot's current position and dimension. |
| `home <name>` | Navigate to saved home in the current dimension. |
| `explore <name> <radius>` | Visit a deterministic sequence of up to 32 local waypoints; radius 2–128. |
| `escape <name>` / `flee <name>` | Choose nearby safe terrain, favoring distance from an attacker. |

## Combat

| Command | Behavior |
|---|---|
| `protect <name> [player]` | Continuous protection and follow with separated formation/combat approach positions; default issuer. |
| `guard <name> [radius]` | Guard the bot's current location; default radius 12, allowed 2–32. |
| `attack <name> nearest` | Engage an eligible nearby threat. |
| `attack <name> <target>` | Attack a living entity selected by name, UUID or selector, subject to safety/PVP. |
| `passive <name>` | Stop task and disable automatic self-defense. A subsequent attack/protect/guard command reenables combat. |

## Mining and collection

| Command | Behavior |
|---|---|
| `mine <name> <block> [amount]` | Scan loaded blocks within 150 blocks on each axis of the starting position. Acquire the needed tool, mine until collected progress reaches amount or no viable target remains, then return to continuously follow the issuer. Default amount 1, maximum 4,096. |
| `dig <name> <x> <y> <z>` | Navigate to and break one block. |
| `break <name> <x> <y> <z>` | Alias of dig. |
| `collect <name> drops` | Collect accessible nearby dropped items; complete after none remain nearby. |
| `collect <name> <item> [amount]` | Collect a specific item; default 1, maximum 4,096. |

Use registry IDs such as `minecraft:iron_ore`. Ordinary iron ore progress counts newly collected raw iron; Silk Touch ore blocks also count. Fortune can collect more than the requested amount on the final block. If a suitable tool is missing or almost broken, the bot receives the minimum valid tool directly through a give-style inventory grant. Stone uses a wooden pickaxe, iron ore a stone pickaxe, diamond ore an iron pickaxe, and ancient debris a diamond pickaxe, according to the loaded vanilla harvest rules. Existing usable better tools are reused. Missing, protected or unreachable resources produce a status message and a switch to following the issuer. The requested amount is per bot when using `all`.

Namespaced identifiers work across all item/block commands. Explicit mining/digging supports anvils and ancient debris; automatic navigation protects both. Mining acquires missing tools; direct `dig`/`break` use existing inventory. Protected targets still fail with a readable error. Mining reports completion/failure once before following the issuer. `stop` cancels mining and its return/follow behavior.

## Tools and construction supply

There is no `/bot craft` command. Bots never use recipes, furnaces or crafting tables. Mining gives the minimum valid harvest tool when one is missing. Build plans and navigation gaps give common cobblestone when fewer than eight safe building blocks remain. Grants are inventory-capacity checked and never replace held items; a full inventory produces a readable task failure.

## Building

| Command | Behavior |
|---|---|
| `place <name> <block> <x> <y> <z>` | Place the specified inventory block with vanilla interaction. |
| `build <name> wall <length> <height>` | Wall one block forward, extending to the bot's right. |
| `build <name> floor <width> <length>` | Floor at foot level minus one, forward and to the right. |
| `build <name> bridge <length>` | One-block-wide forward deck at foot level minus one. |
| `build <name> pillar <height>` | Jump and place underneath, beginning at the bot's feet. |
| `build <name> stairs <length>` | Solid staircase rising one block per forward step. |

Orientation and origin are captured from the **bot** when the command is issued. Dimensions are 1–64. Plans contain at most 4,096 placements. General build commands use disposable inventory materials. Explicit `place` uses the named block. Plans never remove occupied blocks implicitly.

## Schematics

| Command | Behavior |
|---|---|
| `schematic list` | List `.schem` files in the instance's `schematics` folder, the world's `schematics` folder and `config/worldedit/schematics`. |
| `schematic info <file>` | Size, block count, layers, fittings and the full material list. |
| `schematic build <file> [<x> <y> <z>] [none\|cw90\|180\|ccw90]` | Every ordinary companion in your dimension builds the file together. `<x y z>` is the minimum corner; without it the build starts two blocks in front of you, centred, extending away from you. |
| `schematic plan` | The build plan: how many blocks are placed from the ground, from the structure, from scaffold towers or directly, and where the towers go. |
| `schematic status` | Stage, current layer, progress, scaffold block and each builder's task. |
| `schematic stop` | Stop the build; finished blocks and any scaffolding stay in place. |

Sponge `.schem` versions 1–3 are supported (WorldEdit, Litematica export, Amulet). The file name may omit `.schem`; paths are rejected. Block entities (chest contents, sign text) and entities are not reproduced. Fluids, fire and blocks unknown to 26.2 are listed as "not buildable".

Build order:

1. **Clear the site.** Before placing anything, builders break every block that is in the way inside the footprint **and 2 blocks around it**, from the build's base up to **2 blocks above its top**: hills, trees, old walls, flowers, earlier scaffolding. The ground under the build is not dug, grass/vines/snow layers and fluids are left (placement replaces them), and blocks the schematic already wants in that exact cell stay. Work goes from the top layer down so falling blocks never land in cleared space and builders dig down instead of stranding themselves. Chests, beds, spawners, bedrock and other blocks protected from `dig` are left in place and reported. Picked-up drops the build does not need are dropped again whenever a builder needs room for materials or scaffold.
2. **Plan.** With the site clear, the build is planned before anyone moves: the cleared area is snapshotted and every block is simulated in build order, choosing where to stand, whether it is clicked into place or set directly, and where scaffold towers go (outside the walls where possible, shared by many blocks). Builders then follow the plan — taking blocks they can reach from where they already stand first — and only search live when the world differs from it.
3. **Structure, lowest layer to highest.** Builders take blocks from the lowest unfinished layer; a builder may start the next layer only where the block beneath is already finished. Inside a layer, work goes **ring by ring**: first the blocks resting on something built below (for a roof, the ring over the walls), then each ring touching the previous one — inward toward the centre, and outward for eaves. Each builder prefers its own strip of the footprint and the nearest block, and avoids blocks right next to another builder's block or body.
4. **Fittings, also bottom up.** Ladders, doors, torches, beds, lanterns, signs, buttons, levers, carpets, rails, plants and similar attachments are placed only after all structural blocks exist. Door holes stay open as routes until then; placed ladders become climbable routes for higher ones.
5. **Furnishings last.** Chests, barrels, crafting tables, furnaces, smokers, blast furnaces, anvils, enchanting tables, grindstones, lecterns and other containers/workstations are placed after every fitting.
6. **Scaffold removal, highest first.** Every pillar/bridge/support block placed during the build is a dedicated temporary block that the schematic does not use (dirt, otherwise netherrack, cobbled deepslate …). Builders remove them top-down, then the job reports completion.

Builders carry the block they are about to place (navigation swaps in a scaffold block only for the instant it pillars or bridges), carry the right tool while walking to scaffolding, and stow their hand while waiting. All companions open wooden doors and fence gates on their route and close the ones they opened once nobody is standing in them; iron doors are treated as walls.

Supplies are issued with the same give-style grants as other tasks: a diamond pickaxe, shovel and axe if missing, 16 bread if the bot has no food, a stack of scaffold blocks (topped up automatically), and each schematic material just in time — never more than the remaining build needs across all builders. Blocks are placed with the real item through vanilla interaction and the bot's yaw is set so doors, beds and stairs face correctly; orientation variants vanilla picks differently (wall vs floor torch, top slabs, chest facing) are corrected after the real item is consumed. If a block still cannot be placed after repeated attempts while in reach, it is set directly while still consuming the item; `status` counts these.

Coordination and scaffolding: each builder reserves its stand, the head space above it and the whole pillar column it will raise; other builders never stand or pillar there, and their routes treat reserved cells and other builders' bodies as a detour. Routes plan an entire pillar (and the walk onto a structure from its top) in one search. Scaffold removal never takes a block out of a column another builder is standing on; a builder standing on scaffolding digs its own column down first.

Blocked builders escalate in a fixed order. **First, go around:** after one spot fails, spots outside the building's footprint are preferred, reached by scaffolding up the outside. **Then, break through:** after a second failed spot, when no route exists at all, or when a builder is stuck, its routes may break finished schematic blocks. Each one costs as much as about three pillar blocks, so going around still wins whenever it is workable. Every finished block broken this way stays open as a passage and is **sealed at the end** with the block the schematic wants there, from outside the footprint when possible, before scaffold removal. Each block is sealed this way at most twice; after that it is rebuilt like any other missing block. A cell boxed in on all six sides only needs to be in reach to be placed.

If only some builders can get up to the current layer, the others step aside. A builder that fails twice to reach blocks in a layer while another builder is placing blocks on it is benched for that layer: it parks out of the way, and the builders that can reach do the layer. Chat says "bob can't reach layer 5; leaving it to alice". The bench lifts when the layer is finished, when nobody else is reaching it any more, or after 60 seconds, so a benched builder tries again once new scaffolding may have opened a way up. A builder that has placed a block in the layer is never benched there.

No-stall rules: claims expire when a builder stops working on them; a builder that neither moves nor finishes a block for 10 seconds hands its block back and replans, and on a second consecutive stall may break out through finished blocks (which are then rebuilt) and, with at least 6 hearts, accept drops of up to 6 blocks until it makes progress again; failed blocks back off and are skipped after six attempts, then retried once at the end; a job-wide watchdog releases all work if nothing progressed for 60 seconds. Pathfinding treats finished schematic blocks as unbreakable. Removing or reassigning a builder returns its work to the others.

## Inventory

| Command | Behavior |
|---|---|
| `inventory <name>` | Show nonempty inventory/equipment slots and counts. |
| `give <name> <player> <item> [amount]` | Transfer existing inventory to a player within 4 blocks in the same dimension; default 1. Overflow drops at recipient. |
| `drop <name> <item> [amount]` | Drop existing inventory into the world; default 1. |
| `equip <name> best` | Choose existing armor and weapon. |
| `equip <name> <item>` | Equip an existing armor/shield item or select it in the main hand. |
| `eat <name>` | Consume an available safe food using normal use duration, if hungry. |

Amounts for give/drop are 1–2,304. To supply a bot in survival, throw items beside it. For explicit operator testing, vanilla `/give bob minecraft:cobblestone 64` is available. Issued items include the spawn/duty sword, selection wand, missing mining tools, automatic construction cobblestone and one initial stack of dirt on first rectangular guard assignment. Subsequent guard building supplies require explicit refills.

## Configuration and all-bot commands

```text
/bot config <name> breakBlocks on|off
/bot config <name> placeBlocks on|off
/bot config <name> bridge on|off
/bot config <name> sprint on|off
/bot config <name> pvp on|off
/bot config <name> friendlyFire on|off
/bot config <name> maxDistance <blocks>

/bot all stop
/bot all return
/bot all follow
/bot all protect
/bot all mine minecraft:stone 32
/bot mine all minecraft:iron_ore 16
/bot follow all
/bot all sprint on
/bot all build bridge 20
/bot all inventory
/bot all equip best
/bot all config bridge off
/bot all remove
```

`maxDistance` is 4–1,024, default 256; it bounds the current destination's distance from the companion, not an owner tether. The bridge flag controls automatic gap bridging and navigation pillaring; `placeBlocks` controls all placements. Explicit bridge/pillar build plans are user-directed construction and remain usable when automatic bridging is off. The all-bot player commands target the issuing player. Each bot gets a separate task object and navigation state.

## Rectangular area guards and health (1.2.0)

```text
/bot guardarea 29 1 0 19 10 0 2
/bot guard bob area 29 1 0 19 10 0
/bot guard all area 29 1 0 19 10 0
/bot all guard area 29 1 0 19 10 0
/bot stop all
/bot all stop
/bot health bob 40
/bot health all 100
/bot config bob maxHealth 40
/bot config all health 100
```

`guardarea <x1> <y1> <z1> <x2> <y2> <z2> <count>` assigns exactly 1?4 active companions in the command's dimension. Spawn the companions first. Coordinates are inclusive block corners and may be reversed; the example is an 11 ? 10 ? 1 block area. Each axis may span at most 128 blocks. Areas must be within the world border and build height.

Existing guards for the same area are retained first, then eligible companions by name. Companions already guarding a different rectangular area are excluded, allowing separate groups per area. Insufficient available companions fails before replacing any task. Repeating the same area with a smaller count stops the excess guards. `guard <name|all> area ...` explicitly assigns named/all companions and can replace any prior area assignment.

Area duty cancels follow/protect/mining and does not resume them afterward. Guards target eligible Minecraft MONSTER-category entities whose feet are inside the selected area, including slimes and flying hostiles. This category also includes some neutral monsters. Player, owner, companion, pet and friendly-fire protections remain in force. Guards stop targeting monsters that leave and return to safe posts; they continue guarding until stopped or replaced. Navigation detours and emergency survival movement may leave the bounds. Areas only monitor loaded entities and do not force chunks to load.

`health <name|all> <points>` sets maximum health and immediately heals to that maximum. `config <name|all> health|maxHealth <points>` does the same. Range: 1?1,024 points; default 20 points (10 hearts). Maximum health persists across despawn/restart; normal damage, hunger and death still apply.

Every spawn ensures a diamond sword in the bot's inventory. Guard, rectangular guard and protect duty enforce a diamond sword in the main hand, supplying a replacement if lost or broken. Existing swords are reused without repairing them. Full inventory overflow is dropped normally. Food moves to the offhand during duty, retaining the displaced offhand item in inventory. World interactions may briefly select a required tool/block within a server tick; the duty sword is restored by the end of that tick. Other tasks retain ordinary tool/weapon selection.

Protect and rectangular guard assign ranged duty to exactly half of the selected guards (one of two, two of four). Every duty bot automatically receives a Power V Infinity bow and one arrow; only the assigned half equips and uses predictive, full-draw vanilla shots against ranged hostile mobs such as skeletons, while the remaining guards use their diamond swords. Navigation can open hand-operated doors and press levers/buttons on the route. If flowing water stalls a route, the edge is marked blocked and a bounded alternate search is started.

## Guard selection stick (1.3.0)

```text
/bot guardarea all
/bot guardarea bob,tracy
```

Both commands issue/reuse a named **Guard Area Wand** stick and select it in your main hand. `all` snapshots currently active bots in your dimension. Names can be comma-separated (up to four); duplicates, unknown bots and mixed dimensions are rejected. The number of named companions is the guard count.

1. Left-click a block to set corner 1. The block is not broken.
2. Right-click another block to set corner 2 and immediately assign the selected guards. The block/container is not used.
3. A bounded particle outline highlights the full XYZ volume while you hold the stick. Both selected blocks are included; reversed corners work. Select enough vertical height to contain mobs, not just their floor.
4. Left-click again to begin a replacement selection, or run the command again for different bots. `/bot stop all` cancels active guard tasks.

Selections replace follow/protect/other tasks only after a valid second corner. The existing `/bot guardarea <x1> <y1> <z1> <x2> <y2> <z2> <count>` form remains available. Selections are private to the operator and do not persist over disconnect/restart. Holding an ordinary stick does not select areas. A full inventory drops the displaced stack normally.

Give bots common solid blocks such as cobblestone or dirt through normal item drops (or explicit vanilla `/give` when testing). They can place real bridges, steps and pillars to reach guard posts and threats. The first rectangular guard assignment issues 64 dirt; its saved issued flag prevents automatic refills on reassignment, respawn or restart. If the inventory is full, the initial grant retries every 100 ticks until it fits. Once spent, refill with `/give bob minecraft:dirt 64`. Ordinary mining/navigation needs supplied building blocks. If you disabled construction, reenable it with `/bot config all bridge on` and `/bot config all placeBlocks on`. Existing safety and server placement restrictions are respected.

Mining and resource gathering reserve different target blocks and work positions at least five blocks apart in XYZ. Exact block locks also cover navigation breaking. Bots may wait for a mining zone to become free, rather than all trying the same vein at once. Travel and item pickup are not guaranteed to remain five blocks apart.

## Direct supply, ranged guard and interaction (1.8.0)

The table-stall workflow has been removed. There is no `/bot craft` command and bots never use recipes, furnaces or crafting tables.

`/bot mine all minecraft:stone 32` gives each bot a minimum valid wooden pickaxe when needed, then scans loaded terrain within **plus or minus 150 blocks on X, Y and Z** of each mining task's starting position. Iron, diamond and ancient-debris targets receive the appropriate minimum pickaxe tier directly. Navigation and build plans give common cobblestone when fewer than eight safe construction blocks remain. Grants are capacity checked and do not replace held equipment.

## Bot control menu (1.9.0)

`/botui` opens the operator-only in-game control menu. Select a named bot or All at the top, then left-click controls with an empty cursor. Pages cover actions, mining, building, settings, inventory, management and target players. Follow/Return/Protect use the player selected on the Players page (you by default). The guard-area control gives the existing selection wand and closes the menu for corner selection. STOP ALL is always available. See [UI reference](docs/UI-1.9.0.md).
# Flying target stick (1.15.0)

`/bot falcon give_stick` or `/bot falcon get_stick` gives the flying hunter's owner a named stick and selects it. Hold it, aim at any visible mob within 30 blocks and press **R**. The flyer pursues that mob and attacks with its mace, then resumes its circular patrol. Players and other companions cannot be selected. Install this mod and Fabric API on both the server and your Fabric 26.2 client for the R key. The server checks the stick, owner, permission, dimension, distance, line of sight and target before assigning the attack. Automatic hunting only selects visible hostile mobs with open sky above their eyes; this conservatively excludes cave mobs and some mobs under dense cover.
