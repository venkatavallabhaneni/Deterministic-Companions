# Automation fixes 1.4.0 — Minecraft 26.2 / Fabric

This release addresses reported crafting, mining coordination, construction and guard approach failures. Named-bot guard wand commands remain available, including `/bot guardarea bob,tracy` and `/bot guardarea all`.

## Crafting

- Workstation interactions release sneak/item use and temporarily empty the main hand. The held stack is restored in a `finally` block, including full inventories and throwing interaction hooks. No empty storage slot is required to open the workstation.
- Recover existing crafting-grid/cursor inputs before assigning a recipe. Calculate result capacity after subtracting recipe inputs, so ingredients that free a slot permit crafting with an initially full inventory.
- As explicitly requested, grant missing ingredients for one supported recipe batch, at most once per 100 server ticks per companion. At normal 20 TPS this is five seconds; server lag increases wall-clock time. Existing ingredients are matched first, including overlapping ingredient tags. Choose the recipe with the fewest missing inputs; registry order breaks ties deterministically.
- Grants are atomic and never overwrite inventory or repeatedly drop overflow. A full inventory reports failure. Tool acquisition uses the same recipe system and material assistance; the output is still crafted through the vanilla result slot, with normal ingredient consumption and remainders. The old resource-gathering fallback remains for supported non-recipe resources.
- Material assistance is an intentional change from the earlier survival-only implementation. It does not top up guard construction blocks or generate recipe outputs directly. Special recipe classes and smithing remain unsupported.

## Mining coordination

Each bot reserves one exact target and one mining work position. Targets and work positions must be at least five blocks from another bot's corresponding reservation in three-dimensional block coordinates. Reservations are dimension-scoped, refreshed while working, released on cancellation/rejection, and expire if abandoned. Mining and supply gathering use these reservations; navigation breaking has a separate exclusive block lock shared with mining, so two bots cannot concurrently break the same block.

When other miners occupy the available work zones, mining waits and rescans at 100-tick intervals, with a bounded waiting budget. A small vein may be mined serially. Travel corridors and drop pickup do not enforce a five-block separation. Four-chunk loaded scanning and existing per-tick search limits remain in place.

## Construction and task control

The placement loop previously selected a non-hotbar block slot on each candidate face. Selection swaps with the held slot, so a second face could swap the sword back into the hand. Selection now happens once per placement attempt, before trying faces.

Bridge execution can crouch toward an overhanging supported edge when center-position placement fails. Placement failures retry for at most 60 ticks, covering transient collisions and jump timing. Mining interaction search now accepts safe unsupported positions that navigation can construct toward with available blocks. Actual block placement still uses `ServerPlayerGameMode.useItemOn`, consumes inventory, and respects server permissions and world safety checks.

Failed path edges receive a bounded ten-second exclusion so another route can be searched. Blocked persistent navigation retries, and adding construction materials clears old failures/exclusions. Resuming a primary task after survival intervention clears the emergency destination. Guard post rescans only stop navigation if the chosen post changes. Protect/guard select their own threats instead of competing with the generic self-defense branch; combat approach uses a tighter destination tolerance when line of sight is obstructed.

These changes improve the identified paths; they do not guarantee a safe constructible route to every target. Loaded chunks, normal reach, valid attachment faces, entity collision, inventory, protected terrain and bounded searches still constrain movement.

## Guard supplies

The first rectangular guard assignment issues **64 dirt per saved companion**. The issued flag is persisted in the mod's existing metadata; older records default to not issued. Reassignment, despawn/respawn and clean restarts do not replenish used blocks. A full inventory retries the initial grant every 100 ticks until it fits.

Refill explicitly:

```text
/give bob minecraft:dirt 64
/give tracy minecraft:dirt 64
```

`/bot give` retains its earlier meaning: transfer items out of the bot. Sword issuance/enforcement and the four-active-bot cap remain unchanged. No world save is deleted or replaced by this update.

## Verification

The pinned 26.2 sources were inspected for `ServerPlayerGameMode.useItemOn`, sneak/secondary use, crafting menu result updates, slot transfer/consumption and recipe ingredients. Primary online references checked were [Fabric's 26.2 toolchain announcement](https://www.fabricmc.net/2026/06/15/262.html), [Fabric events](https://docs.fabricmc.net/develop/events), and [vanilla recipe extensions](https://docs.fabricmc.net/develop/recipes/extending-vanilla-recipes). Implementation compiles against the project's actual 26.2 dependencies rather than older-version snippets.

The full automated suite has 228 cases. The 28 new regressions exercise held-item restoration, actual vanilla recipe deficits, overlapping ingredient matching, full-inventory result capacity, atomic grants, five-second checks, one-time guard dirt and metadata compatibility, mining spacing/locks and bridge approach geometry. See `TESTING.md` for live-world scenarios. Automated tests do not launch a world; live Minecraft multiplayer behavior has not been verified in this session.

Build and package with Java 25:

```powershell
.\gradlew.bat clean build sourceZip
```
