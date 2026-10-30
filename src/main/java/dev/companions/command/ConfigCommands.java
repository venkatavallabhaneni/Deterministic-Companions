package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.*;
import dev.companions.task.*;
import static dev.companions.command.CommandSupport.*;
import static net.minecraft.commands.Commands.*;

public final class ConfigCommands {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        var config=name();
        for(String key:new String[]{"breakBlocks","placeBlocks","bridge","sprint","pvp","friendlyFire"}) {
            var property=literal(key);
            for(boolean value:new boolean[]{true,false}) property.then(literal(value?"on":"off").executes(c -> run(c,() -> {
                var b=bot(c);switch(key){case "breakBlocks"->b.config.breakBlocks=value;case "placeBlocks"->b.config.placeBlocks=value;case "bridge"->b.config.bridge=value;case "sprint"->b.config.sprint=value;case "pvp"->b.config.pvp=value;case "friendlyFire"->b.config.friendlyFire=value;}
                b.navigation.stop();say(c,key+" = "+value);
            })));
            config.then(property);
        }
        config.then(literal("maxDistance").then(argument("blocks",IntegerArgumentType.integer(4,1024)).executes(c -> run(c,() -> {bot(c).config.maxDistance=IntegerArgumentType.getInteger(c,"blocks");bot(c).navigation.stop();}))));
        root.then(literal("config").then(config));
    }
}
