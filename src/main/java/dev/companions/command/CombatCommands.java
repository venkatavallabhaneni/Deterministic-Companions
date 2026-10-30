package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import dev.companions.task.*;
import static dev.companions.command.CommandSupport.*;
import static net.minecraft.commands.Commands.*;

public final class CombatCommands {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("hunt").then(name().executes(c -> run(c,() -> {
            var bot=bot(c);
            if(!bot.flyer)throw new IllegalArgumentException("Hunt flight requires a flying companion");
            bot.tasks.set(new FlyingHunterTask(bot));
            say(c,bot.player.getPlainTextName()+": hunting hostiles within 30 blocks");
        }))));
        root.then(literal("protect").then(name().executes(c -> run(c,() -> bot(c).tasks.set(new ProtectTask(bot(c),c.getSource().getPlayerOrException()))))
            .then(argument("player",EntityArgument.player()).executes(c -> run(c,() -> bot(c).tasks.set(new ProtectTask(bot(c),EntityArgument.getPlayer(c,"player"))))))));
        root.then(literal("guard").then(name().executes(c -> run(c,() -> bot(c).tasks.set(new GuardTask(bot(c),12))))
            .then(argument("radius",IntegerArgumentType.integer(2,32)).executes(c -> run(c,() -> bot(c).tasks.set(new GuardTask(bot(c),IntegerArgumentType.getInteger(c,"radius"))))))));
        root.then(literal("passive").then(name().executes(c -> run(c,() -> {bot(c).tasks.stop();bot(c).combat.passive=true;}))));
        root.then(literal("attack").then(name().then(literal("nearest").executes(c -> run(c,() -> {
            var b=bot(c);var target=b.combat.nearest();if(target==null)throw new IllegalArgumentException("No nearby eligible threat");b.tasks.set(new AttackTask(b,target));
        }))).then(argument("target",EntityArgument.entity()).executes(c -> run(c,() -> {
            var entity=EntityArgument.getEntity(c,"target");if(!(entity instanceof net.minecraft.world.entity.LivingEntity target))throw new IllegalArgumentException("Target must be living");
            bot(c).tasks.set(new AttackTask(bot(c),target));
        })))));
    }
}
