package dev.companions.command;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import dev.companions.CompanionsMod;
import dev.companions.player.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

public final class CommandSupport {
    private static final ThreadLocal<CompanionController> SELECTED=new ThreadLocal<>();
    private CommandSupport() {}
    @FunctionalInterface public interface Operation { void run() throws CommandSyntaxException; }
    public static int run(CommandContext<CommandSourceStack> ctx, Operation op) throws CommandSyntaxException {
        try {
            if(isAll(ctx)){
                var companions=manager(ctx).all();
                if(companions.isEmpty())throw new IllegalArgumentException("No active companions");
                String bulkCommand=ctx.getNodes().stream().map(n->n.getNode().getName()).filter(n->n.equals("protect")||n.equals("guard")).findFirst().orElse("");
                if(!bulkCommand.isEmpty()){
                    var ordered=companions.stream().sorted(java.util.Comparator.comparing(c->c.player.getPlainTextName().toLowerCase(java.util.Locale.ROOT))).toList();
                    int ranged=(ordered.size()+1)/2;
                    for(int i=0;i<ordered.size();i++){
                        ordered.get(i).rangedDutyOverride=i<ranged;
                        ordered.get(i).formationIndexOverride=i;
                        ordered.get(i).formationCountOverride=ordered.size();
                    }
                }
                int successes=0;
                for(var companion:companions){
                    SELECTED.set(companion);
                    try{op.run();successes++;}
                    catch(CommandSyntaxException|IllegalArgumentException|IllegalStateException e){ctx.getSource().sendFailure(Component.literal(companion.player.getPlainTextName()+": "+e.getMessage()));}
                    finally{SELECTED.remove();}
                }
                for(var companion:manager(ctx).all())manager(ctx).store.save(companion);
                return successes;
            }
            op.run();for(var companion:manager(ctx).all())manager(ctx).store.save(companion);return 1;
        }
        catch (IllegalArgumentException | IllegalStateException e) { throw new SimpleCommandExceptionType(Component.literal(e.getMessage())).create(); }
    }
    public static boolean isAll(CommandContext<CommandSourceStack> ctx){
        if(ctx.getNodes().size()>1 && ctx.getNodes().get(1).getNode().getName().equals("spawn"))return false;
        if(ctx.getNodes().size()>1 && ctx.getNodes().get(1).getNode().getName().equals("all"))return true;
        try{return StringArgumentType.getString(ctx,"name").equalsIgnoreCase("all");}catch(IllegalArgumentException e){return false;}
    }
    public static FakePlayerManager manager(CommandContext<CommandSourceStack> c) { return CompanionsMod.manager(c.getSource().getServer()); }
    public static CompanionController bot(CommandContext<CommandSourceStack> c) { return SELECTED.get()!=null?SELECTED.get():manager(c).get(StringArgumentType.getString(c,"name")); }
    public static void say(CommandContext<CommandSourceStack> c, String text) { c.getSource().sendSuccess(() -> Component.literal(text),false); }
    public static void startTask(CommandContext<CommandSourceStack> context,dev.companions.task.CompanionTask task) {
        var companion=bot(context);
        companion.tasks.set(task,() -> say(context,companion.player.getPlainTextName()+": "+task.getStatus()));
        if(!task.isComplete())say(context,companion.player.getPlainTextName()+": "+task.getStatus());
    }
    public static RequiredArgumentBuilder<CommandSourceStack,String> name() {
        return Commands.argument("name",StringArgumentType.word()).suggests((c,b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),manager(c).all().stream().map(x -> x.player.getPlainTextName())),b));
    }
}
