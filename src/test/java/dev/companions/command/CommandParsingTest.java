package dev.companions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production Brigadier trees with vanilla registries, without a world/server. */
class CommandParsingTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    static CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        var root=Commands.literal("bot");
        WorkCommands.register(root);
        InventoryCommands.register(root);
        GeneralCommands.register(root);
        MovementCommands.register(root);
        CombatCommands.register(root);
        ConfigCommands.register(root);
        AreaGuardCommands.register(root);
        HealthCommands.register(root);
        AllCommands.register(root);
        dispatcher.register(root);
        return dispatcher;
    }

    static CommandContext<CommandSourceStack> parse(String command) {
        var parsed=dispatcher().parse(command,null);
        assertFalse(parsed.getReader().canRead(),() -> "Unparsed: "+parsed.getReader().getRemaining()+"; errors: "+parsed.getExceptions());
        var context=parsed.getContext().build(command);
        assertNotNull(context.getCommand(),"Expected executable command");
        return context;
    }

    @ParameterizedTest @ValueSource(strings={
        "bot mine bob minecraft:anvil", "bot mine bob minecraft:anvil 1",
        "bot mine bob anvil", "bot mine bob minecraft:iron_ore 32",
        "bot mine bob example:ores/tin 2", "bot mine bob minecraft:deepslate_iron_ore 4096",
        "bot place bob minecraft:anvil 10 64 -20", "bot place bob anvil ~ ~1 ~",
        "bot collect bob minecraft:raw_iron", "bot collect bob minecraft:raw_iron 32",
        "bot collect bob drops", "bot give bob alice minecraft:iron_pickaxe",
        "bot give bob alice minecraft:cobblestone 64", "bot drop bob minecraft:anvil",
        "bot drop bob minecraft:cobblestone 64", "bot equip bob minecraft:iron_pickaxe",
        "bot equip bob best", "bot mine bob minecraft:chipped_anvil", "bot mine bob minecraft:damaged_anvil"
    }) void acceptsDocumentedResourceCommands(String command) { parse(command); }

    @ParameterizedTest @ValueSource(strings={
        "bot mine bob minecraft:anvil 0", "bot mine bob minecraft:anvil -1",
        "bot mine bob minecraft:anvil 4097", "bot mine bob minecraft:anvil junk",
        "bot mine bob minecraft:ANVIL", "bot mine bob minecraft:anvil!",
        "bot mine bob minecraft:anvil:extra", "bot place bob minecraft:anvil 1 2",
        "bot drop bob minecraft:stone 0", "bot equip bob minecraft:stone trailing"
    }) void rejectsMalformedCommands(String command) {
        var parsed=dispatcher().parse(command,null);
        assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand()==null);
    }
    @Test void nativeIdentifierContextNormalizesVanillaNamespace() {
        assertEquals(Identifier.parse("minecraft:anvil"),IdentifierArgument.getId(parse("bot mine bob anvil"),"block"));
        assertEquals(Identifier.parse("example:ores/tin"),IdentifierArgument.getId(parse("bot mine bob example:ores/tin 2"),"block"));
    }
    @ParameterizedTest @ValueSource(strings={
        "bot mine all minecraft:stone 32","bot all mine minecraft:stone 32","bot all mine minecraft:anvil",
        "bot all follow","bot follow all","bot all follow alice","bot all return","bot return all alice",
        "bot all protect","bot protect all alice","bot all goto 10 64 -20","bot all stay","bot all stop",
        "bot all sethome","bot home all","bot all inventory","bot all equip best","bot all drop minecraft:stone 4",
        "bot all give alice minecraft:diamond 1","bot all build bridge 20","bot all guard 12","bot all collect drops",
        "bot all config sprint off","bot config all bridge on","bot sprint all on","bot all sprint off",
        "bot sprint bob on","bot all status","bot remove all","bot all remove","bot all break 1 64 2"
    }) void acceptsSprintAndExpandedAllCommands(String command){parse(command);}
    @ParameterizedTest @ValueSource(strings={"bot craft bob minecraft:wooden_pickaxe","bot craft all minecraft:stick 16","bot all craft minecraft:oak_planks 64"})
    void craftCommandIsRemoved(String command){
        var parsed=dispatcher().parse(command,null);
        assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand()==null);
    }
    @ParameterizedTest @ValueSource(strings={"bot build bob schematic house.schem","bot build all schematic castle.schem","bot all build schematic tower.schematic"})
    void schematicCommandIsRemoved(String command){
        var parsed=dispatcher().parse(command,null);
        assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand()==null);
    }
    @Test void bulkDetectionDoesNotTurnSpawnIntoABulkOperation(){
        assertTrue(CommandSupport.isAll(parse("bot mine all minecraft:stone")));
        assertTrue(CommandSupport.isAll(parse("bot all mine minecraft:stone")));
        assertFalse(CommandSupport.isAll(parse("bot mine bob minecraft:stone")));
        assertFalse(CommandSupport.isAll(parse("bot spawn all")));
        assertFalse(CommandSupport.isAll(parse("bot list")));
    }
    @ParameterizedTest @ValueSource(strings={
        "bot guardarea 29 1 0 19 10 0 2","bot guardarea ~ ~ ~ ~10 ~10 ~ 4",
        "bot guard bob area 29 1 0 19 10 0","bot guard all area 29 1 0 19 10 0","bot all guard area 29 1 0 19 10 0",
        "bot health bob 40","bot health all 100","bot all health 100","bot config bob maxHealth 40","bot config all health 50",
        "bot all config maxHealth 60","bot stop all","bot all stop"
    }) void acceptsAreaGuardAndHealthCommands(String command){parse(command);}
    @ParameterizedTest @ValueSource(strings={"bot guardarea 1 2 3 4 5 6 0","bot guardarea 1 2 3 4 5 6 5","bot health all 0","bot health bob 1025","bot health bob NaN","bot guardarea 1 2 3 4 5 6"})
    void rejectsInvalidGuardCountsAndHealth(String command){
        var p=dispatcher().parse(command,null);
        if(command.startsWith("bot guardarea")&&!p.getReader().canRead()&&p.getContext().getCommand()!=null){
            var ctx=p.getContext().build(command);String names=com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"guards");
            assertThrows(IllegalArgumentException.class,()->dev.companions.selection.GuardSelection.parseNames(names));
        }else assertTrue(p.getReader().canRead()||p.getContext().getCommand()==null);
    }
    @ParameterizedTest @ValueSource(strings={"bot guardarea all","bot guardarea bob","bot guardarea bob,tracy","bot guardarea bob, tracy","bot guardarea bob,tracy,alex,steve"})
    void wandAcceptsNativeGreedyStringCommaLists(String command){
        var ctx=parse(command);var names=com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"guards");assertFalse(dev.companions.selection.GuardSelection.parseNames(names).isEmpty());
    }
    @Test void numericAreaCommandStillUsesCoordinatesRatherThanWand(){
        var ctx=parse("bot guardarea 29 1 0 19 10 0 2");assertEquals(2,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"count"));
        assertThrows(IllegalArgumentException.class,()->com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"guards"));
    }
    @Test void inventoryHandlersReadNativeIdentifierAndRejectUnknownItems() {
        assertEquals("minecraft:iron_pickaxe",InventoryCommands.item(parse("bot equip bob minecraft:iron_pickaxe")));
        assertEquals("minecraft:anvil",InventoryCommands.item(parse("bot drop bob anvil")));
        var context=parse("bot drop bob example:not_a_real_item");
        assertEquals("Unknown item: example:not_a_real_item",assertThrows(IllegalArgumentException.class,()->InventoryCommands.item(context)).getMessage());
    }
    @Test void everyResourceArgumentUsesTheNativeSerializableParser() {
        var root=dispatcher().getRoot().getChild("bot");
        for(String command:new String[]{"mine","place"}) {
            var argument=(com.mojang.brigadier.tree.ArgumentCommandNode<?,?>)root.getChild(command).getChild("name").getChild("block");
            assertInstanceOf(IdentifierArgument.class,argument.getType());
        }
        for(String command:new String[]{"collect","drop","equip"}) {
            var argument=(com.mojang.brigadier.tree.ArgumentCommandNode<?,?>)root.getChild(command).getChild("name").getChild("item");
            assertInstanceOf(IdentifierArgument.class,argument.getType());
        }
        var give=(com.mojang.brigadier.tree.ArgumentCommandNode<?,?>)root.getChild("give").getChild("name").getChild("player").getChild("item");
        assertInstanceOf(IdentifierArgument.class,give.getType());
    }
}
