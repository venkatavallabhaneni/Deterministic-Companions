package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import static dev.companions.command.CommandSupport.*;
import static net.minecraft.commands.Commands.*;

public final class InventoryCommands {
    public static String item(CommandContext<CommandSourceStack> c) {
        var id=IdentifierArgument.getId(c,"item");
        if(BuiltInRegistries.ITEM.getOptional(id).isEmpty())throw new IllegalArgumentException("Unknown item: "+id);return id.toString();
    }
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("inventory").then(name().executes(c -> run(c,() -> say(c,bot(c).inventory.describe())))));
        root.then(literal("eat").then(name().executes(c -> run(c,() -> {if(!bot(c).inventory.eat())throw new IllegalStateException("Not hungry or no safe food in inventory");}))));
        for(String kind:new String[]{"give","drop"}) {
            var item=argument("item",IdentifierArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(),b))
                .executes(c -> run(c,() -> transfer(c,kind,1)))
                .then(argument("amount",IntegerArgumentType.integer(1,2304)).executes(c -> run(c,() -> transfer(c,kind,IntegerArgumentType.getInteger(c,"amount")))));
            var bot=name();if(kind.equals("give"))bot.then(argument("player",EntityArgument.player()).then(item));else bot.then(item);
            root.then(literal(kind).then(bot));
        }
        root.then(literal("equip").then(name().then(literal("best").executes(c -> run(c,() -> {bot(c).inventory.armor();bot(c).inventory.weapon();})))
            .then(argument("item",IdentifierArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(),b)).executes(c -> run(c,() -> {
                var bot=bot(c);int slot=bot.inventory.find(item(c));if(slot<0)throw new IllegalArgumentException("Item not in inventory");
                var stack=bot.player.getInventory().getItem(slot);var eq=stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                if(eq!=null) {var old=bot.player.getItemBySlot(eq.slot());bot.player.setItemSlot(eq.slot(),stack);bot.player.getInventory().setItem(slot,old);}
                else if(stack.is(net.minecraft.world.item.Items.SHIELD)) {var old=bot.player.getOffhandItem();bot.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,stack);bot.player.getInventory().setItem(slot,old);}
                else bot.inventory.select(slot);
            })))));
    }
    private static void transfer(CommandContext<CommandSourceStack> c,String kind,int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var recipient=kind.equals("give")?EntityArgument.getPlayer(c,"player"):null;
        var bot=bot(c);
        if(recipient!=null && (recipient.level()!=bot.player.level() || recipient.distanceToSqr(bot.player)>16))throw new IllegalArgumentException("Recipient must be within 4 blocks");
        bot.inventory.transfer(item(c),amount,recipient);
    }
}
