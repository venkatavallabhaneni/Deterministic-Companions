# Bot controls — 1.9.0

Run `/botui` as an operator in game. A vanilla six-row chest menu opens; the mod remains server-side and requires no extra client mod. Implementation uses the Minecraft 26.2 `ChestMenu`, `ContainerInput`, `SimpleMenuProvider` and item components, checked against the installed classes and [Fabric's container-menu documentation](https://docs.fabricmc.net/develop/blocks/container-menus).

Select All or a named bot across the top. Hover over a bot to see task, health, food, position and path status. The bottom row selects pages, and STOP ALL is always available. Status updates once per second while open. Left-click icons with an empty cursor; the menu rejects inventory editing, dragging, shift-click, number keys, dropping and cloning. Each operator has independent menu state. Permissions are checked both when opening and when clicking.

- Actions: follow, return, protect, location guard, guard-area wand, stay, stop, set home, home, go to the operator's current block, explore, escape, passive, attack nearest, collect drops, eat, equipment and detailed status.
- Players: select an online real player for Follow / Return / Protect; defaults to the operator. Lists paginate beyond 18 players.
- Mining: twelve common block presets and per-bot quantities 1, 9, 32, 64 or 128. These retain existing tool supply and mining-to-follow behavior.
- Building: bridge, wall, floor, pillar and stairs; size 5, 10, 20 or 32. Walls are three blocks high, floors square. Origins/facing are each bot's current position/facing. Select one bot to avoid overlapping group builds.
- Settings: explicit on/off buttons for sprint, breaking, placement, bridging, PVP and friendly fire; health/healing presets and maximum distance. A named bot's current settings are shown.
- Inventory: read-only copies of the selected bot's 36 main inventory slots. Equipment/status commands remain available.
- Manage: spawn/restore bob, alice, steve or alex; confirm-before-despawn for selected bots. Despawn preserves saved data. Custom names and arbitrary command parameters remain available through `/bot`.

Actions call existing Brigadier commands as the operator, preserving task replacement, permission checks, bulk ranged assignments, persistence and command feedback. Failures appear in chat; hover over the paper icon for the last UI result. The area-wand button closes the menu so you can select corners in the world. Reopen with `/botui` afterward. Closing the UI does not stop tasks.

Validation: compiled against 26.2; automated command-preset and click-policy tests. These tests do not render a client or exercise network click packets in a live world. Follow the manual checklist in TESTING.md before relying on the new UI in a shared server.
