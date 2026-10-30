package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.*;
import static net.minecraft.commands.Commands.*;
import static dev.companions.command.CommandSupport.*;

public final class HealthCommands {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        root.then(literal("health").then(name().then(value())));
        for(String key:new String[]{"health","maxHealth"})root.then(literal("config").then(name().then(literal(key).then(value()))));
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,Float> value(){
        return argument("health",FloatArgumentType.floatArg(1,dev.companions.config.HealthPolicy.MAX)).executes(ctx->run(ctx,()->{
            var c=bot(ctx);c.health(FloatArgumentType.getFloat(ctx,"health"),true);say(ctx,c.player.getPlainTextName()+": health "+c.player.getHealth()+"/"+c.player.getMaxHealth()+" points");
        }));
    }
}
