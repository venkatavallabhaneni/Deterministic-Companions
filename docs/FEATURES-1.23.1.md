# 1.23.1 — Builders that can't reach a layer leave it to those who can

When only one builder manages to get up to the next level of a structure, the others no longer keep trying, failing, using up the layer's attempts and crowding the one builder that got there.

## Rule

`schematic/ReachTracker` (pure logic) records, per builder and per layer (phase and height):

- **reached**: the builder completed a block in that layer.
- **missed**: a failed attempt caused by not getting to the block. That means no spot could click or see it, no working spot after several tries, timed out reaching it, or no progress for 10 s. Refused placements, a full inventory and errors do not count.

A builder with 2 misses in the active layer and no completions there is **benched** for that layer while any other active builder has completed a block in it. `SchematicJob.claimNext` gives a benched builder no work. Its task shows "only alice can reach layer 5; waiting", it parks out of the way, and chat reports it once per layer.

The bench lifts:
- when the layer is finished, because the bench is per layer;
- as soon as no other active builder is reaching that layer, for example because it left;
- after `BENCH_TICKS` = 60 s, with its misses reset, so it tries again once new scaffolding may have opened a way up.

A builder that has placed a block in the layer is never benched there.

The builder or builders that can reach take every block of the layer. Strips and spreading only shape the order, not who may work.

## Tests

`SchematicBuildTest.onlyTheBuilderThatReachesALayerKeepsWorkingOnIt` and `aBenchLiftsWhenNobodyElseReachesTheLayer` cover the rule.
