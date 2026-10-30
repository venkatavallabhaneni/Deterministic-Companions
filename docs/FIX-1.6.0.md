# Crafting-table stall fix — 1.6.0

The previous release could leave a fake player looking at a crafting table until the task timeout. The table interaction path relied on the interaction result alone. A local fake connection can consume that interaction result without receiving the normal client menu packet, so the controller believed it had interacted even when its `containerMenu` was still the inventory menu.

The server action controller now verifies that the menu actually changed. If the normal `ServerPlayerGameMode.useItemOn` path consumes the action without opening the menu, it invokes the table's own server `MenuProvider` through `ServerPlayer.openMenu` as a same-thread fallback. This keeps the interaction server-authoritative and uses the vanilla `CraftingMenu`; it does not set blocks or recipe outputs directly.

Recipe input placement now forces a synchronous menu slot update and broadcasts the menu state before reading the result slot. The result is taken only through vanilla `quickMoveStack`, preserving normal recipe validation, input consumption and remainders.

Workstation and task status remain bounded. Table preparation has a 1,200-tick limit and alternate placement candidates; task cancellation/finish closes the menu, clears sneak/use state, releases reservations and safely stows held equipment. A bot that cannot place or open its private table reports the concrete blocked status instead of staring until a generic mining timeout.

## API verification

The fix was checked against the project's pinned Minecraft 26.2 sources: `ServerPlayer.openMenu` creates and installs the server menu, `CraftingTableBlock.useWithoutItem` opens its `CraftingMenu`, and `CraftingMenu.slotsChanged` recomputes the result slot. Fabric's current 26.2 documentation describes workstations as server menus and recipe results as menu-driven; see [Fabric workstations](https://docs.fabricmc.net/develop/blocks/workstations) and [Fabric recipe extensions](https://docs.fabricmc.net/develop/recipes/extending-vanilla-recipes).

The full automated suite now has 251 tests with zero failures, including bounded mining retry and automatic construction-supply coverage. Tests do not launch a world; run the manual crafting scenario in `TESTING.md` to verify a connected 26.2 server.
