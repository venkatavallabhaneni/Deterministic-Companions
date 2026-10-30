# 1.0.1: namespaced commands and deliberate anvil mining

## Reproduced failure

`/bot mine bob minecraft:anvil` failed in the command parser before mining started. Production code used `StringArgumentType.word()` for registry IDs. Mojang's [Brigadier StringArgumentType](https://github.com/Mojang/brigadier/blob/master/src/main/java/com/mojang/brigadier/arguments/StringArgumentType.java) delegates this to `StringReader.readUnquotedString()`, whose [accepted characters](https://github.com/Mojang/brigadier/blob/master/src/main/java/com/mojang/brigadier/StringReader.java) exclude `:` and `/`.

A regression test against the real registered command builders reproduced 15 failing namespaced commands before the fix. The same suite passes after switching to Minecraft 26.2's `IdentifierArgument.id()` and matching `IdentifierArgument.getId()` accessors. This uses a native client-serializable argument type, preserving optional amount and coordinate arguments rather than consuming the remaining command as one string.

Affected commands corrected: `mine`, `place`, `collect <item>`, `give`, `drop`, and `equip <item>`. Both `anvil` and `minecraft:anvil` work; identifiers with namespace/path separators are parsed correctly. Invalid IDs and invalid numeric arguments produce command errors. See Fabric's current [26.2 command argument documentation](https://docs.fabricmc.net/develop/commands/arguments).

## Related mining corrections

- The safety policy previously excluded anvils even when directly named in a mining command. Explicit mine/dig/break requests now permit normal, chipped, and damaged anvils. Navigation obstacle clearing and automatic building-material selection still protect all anvils. The exception does not permit containers, valuable blocks, unbreakable blocks, or hypothetical anvil block entities.
- Mine commands reject disabled breaking, protected/unbreakable targets, air/fluid targets, and missing usable harvest tools before replacing the active task. Bob needs a pickaxe in his actual inventory to mine an anvil. Dig/break also check harvest tools before destroying a block requiring one.
- Work tasks send a start message and one completion/failure message to the command issuer. Cancellation clears the previous completion callback. Missing targets, missing tools, denied interactions and unloaded targets are visible without repeatedly polling `/bot status`.
- A selected mining target that unloads now ends the task without reading/loading its chunk. A tool that breaks mid-task produces an explicit failure instead of rejecting successive targets with a misleading status.
- Breaking still uses vanilla START/STOP/ABORT actions. The official [Fabric 26.2 game-mode mixin](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-events-interaction-v0/src/main/java/net/fabricmc/fabric/mixin/event/interaction/ServerPlayerGameModeMixin.java) confirms this path invokes Fabric attack-block and block-break hooks. Server spawn protection and hazard checks remain active.

## Verification

- 17 original logic tests.
- 32 production-command parser/identifier tests, including the exact reported command, optional amounts, namespaced/slash IDs, malformed inputs and native ID retrieval.
- 11 explicit-anvil safety and task-notification regression cases.
- 5 tests using actual Minecraft 26.2 block properties and shipped datapack resources. All three anvil variants require a correct harvesting tool, appear in the anvil tag referenced by the pickaxe mining tag, and drop their matching block item in vanilla loot data.

These tests bootstrap vanilla registries and exercise production command registration without launching a world. They verify command handling, policy and vanilla anvil data, not full in-world pathfinding/mining. Full gameplay validation remains pending; the development server's EULA remains unaccepted.

## Upgrade and reproduce

Stop Minecraft/the server, remove `deterministic-companions-1.0.0.jar` from `mods`, and install **only** `deterministic-companions-1.0.1.jar`. Keep the matching Fabric API JAR. Restart; do not leave both mod versions installed. Existing companion UUIDs and saved data are unchanged.

Give Bob a usable pickaxe through normal item pickup, place an anvil within the local search area and outside spawn protection, then run:

```text
/bot mine bob minecraft:anvil
```

The omitted amount defaults to one. Test chipped and damaged variants separately by their registry IDs. No command creates a pickaxe or anvil automatically.
