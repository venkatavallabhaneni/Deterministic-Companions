# 1.1.0: crafting, wider mining, group commands and sprinting

Built for Minecraft Java 26.2, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2 and Java 25.

## Behavior

- `/bot mine all minecraft:stone 32` and `/bot all mine minecraft:stone 32` each assign an independent task to every active bot. Amount is per companion.
- Mining searches loaded sections within 64 blocks of its initial position on each axis. It checks at most 1,024 cells and eight section palettes per tick per bot, retaining at most 32 candidate targets. Navigation remains incremental and bounded separately.
- Missing effective tools trigger a bounded gathering/crafting supply task: logs, planks, sticks, a crafting table, wooden/stone tools, a furnace and smelted iron, then diamonds when a diamond tool is necessary. Existing correct usable tools are preferred. Vanilla harvest tags and item components decide the tier; the progression does not craft netherite upgrades.
- Gathering and mining reserve their current target across companions. Claims expire and are released on task cancellation/completion. This prevents duplicate block work; item pickup and player collisions remain normal Minecraft behavior.
- Mining completion or a reported resource/route failure changes into continuous follow of the requesting player. Explicit stop or replacement cancels both phases.
- `/bot craft <name|all> <item> [amount]` uses loaded shaped/shapeless recipes, vanilla crafting grids and result slots. Ingredients, durability, remainders and complete output batches are real. Generic crafting requires ingredients in inventory; mining tool acquisition gathers its prerequisites.
- Iron preparation uses a furnace placed from the bot's inventory, normal fuel and normal furnace ticks. Inputs/outputs remain in the world if work is interrupted. Existing unrelated furnaces are not used as a source of items.
- Both group command forms cover commands with a companion name, including inventory, equip, craft, mining, movement, combat, configuration, construction and removal. `all` is reserved as a name. Spawn/list retain their existing forms.
- Sprinting defaults on for ordinary walking edges when food is above 6, the bot is not eating and is not sneaking. `/bot sprint <name|all> on|off` and `/bot config <name|all> sprint on|off` persist the setting.

## Verification

The release compiles against actual unobfuscated Minecraft 26.2 classes. The JUnit suite has 130 passing tests. New tests load vanilla block/item tags, initialize 26.2 item components, decode shipped recipes, and verify actual wood/stone/iron/diamond harvest requirements. Other tests cover input allocation without double spending, bounded backtracking, the 64-block boundary, mining-to-follow lifecycle, reservation expiry and production Brigadier command parsing.

World-level crafting, gathering, furnace and movement scenarios are documented in `TESTING.md` and remain unverified in a running world. No EULA acceptance or world save changes were made to claim those tests as passed.

## API sources checked

- [Fabric for Minecraft 26.2](https://www.fabricmc.net/2026/06/15/262.html): current Java/toolchain and registry changes.
- [Fabric 26.2 workstations](https://docs.fabricmc.net/develop/blocks/workstations): vanilla menu/result-slot handling, including crafting remainders.
- [Extending vanilla recipes](https://docs.fabricmc.net/develop/recipes/extending-vanilla-recipes): using existing crafting and workstation behavior.
- [Minecraft: Netherite Scrap](https://www.minecraft.net/fi-fi/article/taking-inventory--netherite-scrap): ancient debris requires a diamond pickaxe.

Signatures were additionally checked in the downloaded Minecraft 26.2 sources: `AbstractCraftingMenu.getInputGridSlots/getResultSlot`, `CraftingMenu.quickMoveStack`, `ResultSlot.onTake/getRemainingItems`, `FurnaceMenu`, `RecipeManager.getRecipes`, `CraftingRecipe.assemble`, `ItemStack.isCorrectToolForDrops`, `LevelChunkSection.maybeHas`, and registry/item component initialization. Compilation and data tests validate those signatures and data; they do not substitute for the listed world scenarios.
