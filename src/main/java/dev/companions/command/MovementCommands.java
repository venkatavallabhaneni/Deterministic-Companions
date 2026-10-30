package dev.companions.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import dev.companions.task.*;
import net.minecraft.world.phys.Vec3;
import static dev.companions.command.CommandSupport.*;
import static net.minecraft.commands.Commands.*;

public final class MovementCommands {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        var sprint=name();
        for(boolean enabled:new boolean[]{true,false})sprint.then(literal(enabled?"on":"off").executes(c->run(c,()->{
            bot(c).config.sprint=enabled;if(!enabled)bot(c).player.setSprinting(false);say(c,bot(c).player.getPlainTextName()+": sprint "+(enabled?"on":"off"));
        })));
        root.then(literal("sprint").then(sprint));
        for(String kind:new String[]{"come","return","follow"}) {
            var node=name().executes(c -> run(c,() -> track(c,kind,c.getSource().getPlayerOrException())));
            if(!kind.equals("come")) node.then(argument("player",EntityArgument.player()).executes(c -> run(c,() -> track(c,kind,EntityArgument.getPlayer(c,"player")))));
            root.then(literal(kind).then(node));
        }
        root.then(literal("goto").then(name().then(argument("pos",BlockPosArgument.blockPos()).executes(c -> run(c,() -> bot(c).tasks.set(new GoToTask(bot(c),Vec3.atBottomCenterOf(BlockPosArgument.getBlockPos(c,"pos")))))))));
        root.then(literal("stop").then(name().executes(c -> run(c,() -> bot(c).tasks.stop()))));
        root.then(literal("stay").then(name().executes(c -> run(c,() -> bot(c).tasks.set(new StayTask(bot(c)))))));
        root.then(literal("sethome").then(name().executes(c -> run(c,() -> {var b=bot(c);b.home=b.player.blockPosition();b.homeDimension=b.player.level().dimension().identifier().toString();say(c,"Home saved at "+b.home.toShortString());}))));
        root.then(literal("home").then(name().executes(c -> run(c,() -> bot(c).tasks.set(new HomeTask(bot(c)))))));
        root.then(literal("explore").then(name().then(argument("radius",IntegerArgumentType.integer(2,128)).executes(c -> run(c,() -> bot(c).tasks.set(new ExploreTask(bot(c),IntegerArgumentType.getInteger(c,"radius"))))))));
        for(String kind:new String[]{"escape","flee"}) root.then(literal(kind).then(name().executes(c -> run(c,() -> bot(c).tasks.set(new EscapeTask(bot(c)))))));
    }
    private static void track(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,String kind,net.minecraft.server.level.ServerPlayer player) {
        var b=bot(c);var target=new PlayerTrackingTarget(b,player);
        b.tasks.set(switch(kind){case "follow"->new FollowTask(target);case "come"->new ComeTask(target);default->new ReturnTask(target);});
    }
}
