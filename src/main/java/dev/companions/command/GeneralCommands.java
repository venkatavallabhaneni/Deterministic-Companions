package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import dev.companions.config.CompanionConfig;
import static dev.companions.command.CommandSupport.*;
import static net.minecraft.commands.Commands.literal;

public final class GeneralCommands {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.argument("flyerName",StringArgumentType.word())
            .then(literal("give_stick").executes(GeneralCommands::giveStick))
            .then(literal("get_stick").executes(GeneralCommands::giveStick)));
        root.then(literal("spawn").then(Commands.argument("name",StringArgumentType.word()).executes(c -> run(c,() -> {
            var bot=manager(c).spawn(StringArgumentType.getString(c,"name"),c.getSource().getPlayerOrException()); say(c,"Spawned "+bot.player.getPlainTextName());
        })).then(literal("flyer").executes(c -> run(c,() -> {
            var bot=manager(c).spawn(StringArgumentType.getString(c,"name"),c.getSource().getPlayerOrException(),true);
            say(c,"Spawned flying companion "+bot.player.getPlainTextName());
        })))));
        root.then(literal("remove").then(name().executes(c -> run(c,() -> { manager(c).remove(bot(c).player.getPlainTextName()); say(c,"Despawned; player data retained"); }))));
        root.then(literal("status").then(name().executes(c -> run(c,() -> say(c,bot(c).status())))));
        root.then(literal("list").executes(c -> run(c,() -> {
            long normal=manager(c).all().stream().filter(b->!b.flyer).count();
            long flyers=manager(c).all().stream().filter(b->b.flyer).count();
            StringBuilder text=new StringBuilder("Companions: "+normal+"/"+CompanionConfig.MAX_ACTIVE+"; flying: "+flyers+"/"+CompanionConfig.MAX_FLYERS);
            for(var b:manager(c).all()) {
                text.append("\n").append(b.player.getPlainTextName()).append(b.flyer?" [FLYER]":"").append(" - ").append(b.tasks.status());
                if(c.getSource().getEntity()!=null && c.getSource().getLevel()==b.player.level()) text.append(" - ").append(Math.round(Math.sqrt(b.player.position().distanceToSqr(c.getSource().getPosition())))).append(" blocks away");
            }
            say(c,text.toString());
        })));
    }
    private static int giveStick(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return run(c,() -> {
            var sender=c.getSource().getPlayerOrException();
            var flyer=manager(c).get(StringArgumentType.getString(c,"flyerName"));
            if(!flyer.flyer)throw new IllegalArgumentException("This companion is not a flying hunter");
            if(!flyer.owner.equals(sender.getUUID()))throw new IllegalArgumentException("Only the flying hunter's owner may target it");
            dev.companions.selection.FlyingTargetStick.give(sender,flyer);
            say(c,"Hold the Flying Target Stick, aim at any visible mob within 30 blocks and press R");
        });
    }
}
