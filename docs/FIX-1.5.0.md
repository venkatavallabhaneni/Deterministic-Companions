# Private crafting and mining fixes — 1.5.0

Target: Minecraft Java 26.2, Fabric, Java 25.

- Every mining task prepares an individually owned crafting table before tool preparation and scanning. Generic crafting also uses the owned table, except the table itself, which is bootstrapped in the vanilla 2x2 grid. No nearby public or other companion's table is adopted.
- Separate table-site reservations keep active companions' sites at least five blocks apart. Each bot retains its own vanilla menu/inventory. Ownership coordinates are saved in the existing metadata; old records without coordinates remain readable. Valid tables within 48 blocks are reused. Missing, unloaded, distant or cross-dimension tables cause fresh preparation; old world blocks are retained.
- Table preparation completes before recipe ingredients are supplied. This removes competing nested table-supply work from the generic crafting loop. Missing materials for the table and subsequent recipes are supplied on the existing per-bot 100-tick check. Actual recipe outputs still come through vanilla crafting/result slots.
- Preparation reports site search, placement and approach status. It checks at most 96 candidate sites per call, tries alternate sites after failures and has a 1,200-tick preparation budget. Blocked permissions or terrain produce a reason instead of a silent long-running loop.
- Held items are stowed after crafting and task completion/cancellation: select an empty hotbar slot or move the held stack into empty storage. No item is discarded to empty the hand. A completely full inventory retains the stack, and workstation interaction still uses the exception-safe temporary empty-hand operation. Changing equipment ends the previous item-use action. Ending a task closes its menu and clears use/sneak state.
- Mining and related resource scanning cover a cuboid extending **150 blocks in each direction on X, Y and Z** from the scan origin, including boundary corners. Mining status reports section progress. Only loaded chunks are inspected; no background world reads or forced generation were added. The existing 1,024-cell/eight-section scan budget and local A* budgets remain unchanged. Per-target mining travel timeout increased to 4,000 ticks for longer routes.

The configured navigation `maxDistance` still applies. The default 256 may exclude the farthest three-axis corners of the scan cuboid; set it to 320 if those routes are desired. Movement, safe supports, real block attachment faces, inventory capacity and server permissions remain requirements. There is no guarantee of reaching every scanned block.

## Verification

247 automated tests pass, including 19 new private-table/equipment/radius cases and updated 150/151 scan-boundary assertions. New recipe tests decode the actual vanilla crafting-table recipe and verify isolated ingredients and outputs for four inventories. Tests do not launch a server world.

Inspected the pinned Minecraft 26.2 `Slot`, `TransientCraftingContainer`, `AbstractCraftingMenu`, `CraftingMenu`, and `ServerPlayerGameMode` sources. `Slot.set` delegates to the crafting container, which calls menu recipe updates; vanilla output extraction remains responsible for consumption/remainders. Online references checked: [Fabric workstations](https://docs.fabricmc.net/develop/blocks/workstations), [vanilla recipe extensions](https://docs.fabricmc.net/develop/recipes/extending-vanilla-recipes).

Live-world scenarios are listed in `TESTING.md` and remain unverified in this session.

Build/package with Java 25:

```powershell
.\gradlew.bat clean build sourceZip
```

Stop the server, replace the old companion JAR with `deterministic-companions-1.5.0.jar`, and restart. Keep Fabric API installed. Saved companion inventories are preserved.
