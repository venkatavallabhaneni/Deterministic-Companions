package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.literal;

/** Mirrors every companion command without its name argument; callbacks share the same bulk executor. */
public final class AllCommands {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        var all=literal("all");
        for(var command:root.getArguments()){
            if(command.getName().equals("spawn")||command.getName().equals("all"))continue;
            var name=command.getChild("name");if(name==null)continue;
            var alias=literal(command.getName());
            if(name.getCommand()!=null)alias.executes(name.getCommand());
            for(var child:name.getChildren())alias.then(child);
            all.then(alias);
        }
        root.then(all);
    }
}
