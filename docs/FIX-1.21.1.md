# 1.21.1 — Chests no longer join into half-cut double chests

Symptom: when two chests sat next to each other, they rendered as a broken double chest. Each half looked cut off, with one half facing a different way.

## Cause

Vanilla `ChestBlock.getStateForPlacement` joins a new chest to an existing one when the player is **sneaking** and clicks a **horizontal face of another chest**. The new chest then takes the clicked chest's facing and becomes its LEFT/RIGHT half. `SchematicPlacer` always sneaks, so clicking a chest or crafting table never opens it. When the floor face was not clickable it fell back to a side face, which could be the neighbouring chest. The follow-up correction then turned only the new half to the schematic's facing. It ignored the pairing (`type` was not a compared property), so the two halves of the joined chest disagreed.

## Fix

- When placing a chest, `SchematicPlacer` never clicks the side face of another chest.
- `PlacementRules.matches`/`adjust` compare and set chest `type` (single/left/right) as well as slab `type`.
- `SchematicPlacer.pairChest` sets a chest's facing and pairing and keeps both halves consistent. Any neighbouring chest that believes it is paired with the cell is made single again. A schematic double-chest half pairs only once its partner exists with the same facing, and then both halves are set. It is used after normal placement, after the last-resort direct set, and when realigning.
- `SchematicBuildTask` handles a cell that already holds the right block with the wrong orientation or pairing. Examples are a chest joined to the wrong neighbour or a door with the wrong hinge. It fixes the block in place with `SchematicPlacer.realign`, using no new item, instead of skipping it as a protected block. A double slab is still rebuilt, because it needs its second slab.

Half-cut chests left inside a schematic's footprint by an earlier build are realigned when that schematic is built again.
