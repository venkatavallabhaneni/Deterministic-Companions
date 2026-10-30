package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import dev.companions.task.*;
import dev.companions.building.BuildPlan;
import java.util.*;
import static dev.companions.command.CommandSupport.*;
import static net.minecraft.commands.Commands.*;

public final class WorkCommands {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        var mine=argument("block",IdentifierArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(),b))
            .executes(c -> run(c,() -> mine(c,1))).then(argument("amount",IntegerArgumentType.integer(1,4096)).executes(c -> run(c,() -> mine(c,IntegerArgumentType.getInteger(c,"amount")))));
        root.then(literal("mine").then(name().then(mine)));
        for(String kind:new String[]{"dig","break"}) root.then(literal(kind).then(name().then(argument("pos",BlockPosArgument.blockPos()).executes(c -> run(c,() -> startTask(c,new BreakTask(bot(c),BlockPosArgument.getBlockPos(c,"pos"))))))));
        var collect=name().then(literal("drops").executes(c -> run(c,() -> startTask(c,new CollectTask(bot(c),null,1)))))
            .then(argument("item",IdentifierArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(),b)).executes(c -> run(c,() -> collect(c,1)))
                .then(argument("amount",IntegerArgumentType.integer(1,4096)).executes(c -> run(c,() -> collect(c,IntegerArgumentType.getInteger(c,"amount"))))));
        root.then(literal("collect").then(collect));
        root.then(literal("place").then(name().then(argument("block",IdentifierArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(),b)).then(argument("pos",BlockPosArgument.blockPos()).executes(c -> run(c,() -> {
            String id=IdentifierArgument.getId(c,"block").toString();
            if(BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).isEmpty())throw new IllegalArgumentException("Unknown block: "+id);
            var plan=new BuildPlan("place",List.of(dev.companions.navigation.WorldNeighbors.cell(BlockPosArgument.getBlockPos(c,"pos"))),Set.of(),id);
            startTask(c,new BuildTask(bot(c),plan));
        }))))));
        var build=name();
        for(String shape:new String[]{"wall","floor","bridge","pillar","stairs"}) {
            String first=shape.equals("floor")?"width":shape.equals("pillar")?"height":"length";
            var size=argument(first,IntegerArgumentType.integer(1,64));
            if(shape.equals("wall")||shape.equals("floor")) {
                String second=shape.equals("wall")?"height":"length";
                size.then(argument(second,IntegerArgumentType.integer(1,64)).executes(c -> run(c,() -> build(c,shape,IntegerArgumentType.getInteger(c,first),IntegerArgumentType.getInteger(c,second)))));
            }else size.executes(c -> run(c,() -> build(c,shape,IntegerArgumentType.getInteger(c,first),1)));
            build.then(literal(shape).then(size));
        }
        root.then(literal("build").then(build));
    }
    private static void mine(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var id=IdentifierArgument.getId(c,"block");
        var block=BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(()->new IllegalArgumentException("Unknown block: "+id));
        var companion=bot(c);var player=c.getSource().getPlayerOrException();
        var mining=new MineTask(companion,block,amount);
        startTask(c,new MineAndFollowTask(mining,new FollowTask(new PlayerTrackingTarget(companion,player)),
            ()->say(c,companion.player.getPlainTextName()+": "+mining.getStatus()+"; returning to follow "+player.getPlainTextName())));
    }
    private static void collect(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,int amount) {startTask(c,new CollectTask(bot(c),InventoryCommands.item(c),amount));}
    private static void build(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,String shape,int a,int b) {
        var bot=bot(c);var d=bot.player.getDirection();
        startTask(c,new BuildTask(bot,BuildPlan.generate(shape,dev.companions.navigation.WorldNeighbors.cell(bot.player.blockPosition()),d.getStepX(),d.getStepZ(),a,b)));
    }
}
