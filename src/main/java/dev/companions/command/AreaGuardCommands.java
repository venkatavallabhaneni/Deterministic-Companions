package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import dev.companions.combat.*;
import dev.companions.player.CompanionController;
import dev.companions.task.AreaGuardTask;
import java.util.*;
import static net.minecraft.commands.Commands.*;
import static dev.companions.command.CommandSupport.*;

public final class AreaGuardCommands {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        root.then(literal("guardarea").then(argument("from",BlockPosArgument.blockPos()).then(argument("to",BlockPosArgument.blockPos())
            .then(argument("count",IntegerArgumentType.integer(1,dev.companions.config.CompanionConfig.MAX_ACTIVE)).executes(ctx->run(ctx,()->assign(ctx)))))));
        root.then(literal("guardarea").then(argument("guards",com.mojang.brigadier.arguments.StringArgumentType.greedyString())
            .suggests((ctx,b)->SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),manager(ctx).all().stream().map(c->c.player.getPlainTextName())),b))
            .executes(ctx->run(ctx,()->manager(ctx).wand.begin(ctx.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"guards"))))));
        root.then(literal("guard").then(name().then(literal("area").then(argument("from",BlockPosArgument.blockPos()).then(argument("to",BlockPosArgument.blockPos())
            .executes(ctx->run(ctx,()->{var area=area(ctx);validate(ctx,area);if(bot(ctx).player.level()!=ctx.getSource().getLevel())throw new IllegalArgumentException("Companion must be in the command's dimension");startTask(ctx,new AreaGuardTask(bot(ctx),area));})))))));
    }
    private static GuardArea area(CommandContext<CommandSourceStack> ctx){
        var a=BlockPosArgument.getBlockPos(ctx,"from");var b=BlockPosArgument.getBlockPos(ctx,"to");
        return GuardArea.between(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ());
    }
    private static void validate(CommandContext<CommandSourceStack> ctx,GuardArea a){
        validateArea(ctx.getSource().getLevel(),a);
    }
    public static void validateArea(net.minecraft.server.level.ServerLevel level,GuardArea a){
        for(var p:List.of(new net.minecraft.core.BlockPos(a.minX(),a.minY(),a.minZ()),new net.minecraft.core.BlockPos(a.maxX(),a.maxY(),a.maxZ())))
            if(level.isOutsideBuildHeight(p)||!level.getWorldBorder().isWithinBounds(p))throw new IllegalArgumentException("Guard area lies outside the world's build height or border");
    }
    private static boolean same(CompanionController c,GuardArea area,String dimension){return c.tasks.current() instanceof AreaGuardTask t&&t.area.equals(area)&&t.dimension.equals(dimension);}
    private static void assign(CommandContext<CommandSourceStack> ctx){
        GuardArea area=area(ctx);validate(ctx,area);var level=ctx.getSource().getLevel();String dimension=level.dimension().identifier().toString();
        var eligible=manager(ctx).all().stream().filter(c->c.player.level()==level)
            .filter(c->!(c.tasks.current() instanceof AreaGuardTask)||same(c,area,dimension))
            .sorted(Comparator.comparingInt((CompanionController c)->same(c,area,dimension)?0:1).thenComparing(c->c.player.getPlainTextName())).toList();
        var selected=GuardAssignment.choose(eligible,IntegerArgumentType.getInteger(ctx,"count"));
        for(var c:eligible)if(same(c,area,dimension)&&!selected.contains(c))c.tasks.stop();
        for(int i=0;i<selected.size();i++){var c=selected.get(i);c.tasks.set(new AreaGuardTask(c,area,i,selected.size()));}
        say(ctx,"Guarding "+area.describe()+" with "+selected.size()+" companion(s): "+String.join(", ",selected.stream().map(c->c.player.getPlainTextName()).toList()));
    }
}
