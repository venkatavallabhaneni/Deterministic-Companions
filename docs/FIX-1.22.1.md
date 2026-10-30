# 1.22.1 — Blocks are no longer abandoned so easily

Symptom (21_advanced_test_house): the outer rings of the upstairs floor were built, but its 3 × 5 centre was missing. The builders went on to put up the upstairs walls over the hole, and left one-high dirt rows on the ground floor beneath it.

A grid copy of the house shows a working stand for every block of that centre ring, both from the ground floor and from on top of the finished part of the floor. Reach was not the problem. The build gave up on those blocks and moved on.

## Causes

1. **Interruptions counted as failures.** A builder that stopped to eat or fight lost its claim after 10 s, and the expiry counted as a failed attempt on the block.
2. **Idle builders retried at once, and every retry counted.** With four builders, a block having temporary trouble could use up its 6 attempts in well under a minute.
3. **One failed round skipped a block, and its layer was then treated as finished.** Upper layers were built over the gap, and the block was retried only at the very end.
4. **Removal could happen from on top over a long drop.** The stand finder could choose to stand on the block being broken. Breaking it was then refused because the drop below was too long, which counted as another failure.
5. **Navigation could put temporary scaffold into cells the layer was about to need.** Those blocks then had to be dug out again.

The dirt rows came from the knock-on effect. Once a ring block was skipped, the next ring had nothing to click against, so builders put up support columns under it.

## Fixes

- **Claim expiry** (eating, fighting, removed builder) returns the block with no failure counted. This applies to scaffold and clearing claims too.
- **Second pass per layer.** When every other block of a layer is finished, each block skipped in it gets one more full round (`BuildSchedule.secondPass`) before the layer is left. Attempts per round are now 8. The end-of-build retry still runs as well.
- **Escalation.** After 3 failed attempts, or on its second pass, a block is placed by "aim and place": the builder stands anywhere with the cell itself in reach and in view, tries a normal placement, then sets the block directly, still consuming the real item.
- **Explicit reach modes** in `SchematicBuildTask.approach`: `FACE` (normal placement), `CENTRE` (realigning, clearing a cell, escalated placement, never from on top) and `ON_TOP` (removal). `StandFinder.find(..., onTop, ...)` offers a spot directly on the target only when there is footing within 3 blocks below it.
- **No breaking from on top over a long drop.** A builder standing on a block it may not break steps off and breaks it from the side, instead of recording a failure.
- **Scaffold stays out of soon-needed cells.** `ConstructionPolicy.placementCost` adds route cost 40 for a pillar or bridge block placed into any cell the build still has to fill in the current or next layer, on top of other builders' reserved cells.
- **Reasons are reported.** Every failed attempt records why: no spot can click a face, no spot can see it, timed out, placement refused, no progress, inventory full, and so on. When a block is skipped, chat shows the block, its position and the reason, at most once every 5 s with a count of others. `/bot schematic status` lists the last five skips.

`SchematicBuildTest.aSkippedBlockGetsASecondPassBeforeItsLayerIsLeft` and `SchematicStandTest.neverDigsOutABlockFromOnTopOverALongDrop` cover the new rules. The long-skip test now expects two full rounds.
