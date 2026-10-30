package dev.companions.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.companions.schematic.*;
import dev.companions.task.SchematicBuildTask;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import static dev.companions.command.CommandSupport.*;
import static net.minecraft.commands.Commands.*;

/** /bot schematic list|info|build|status|stop — every ordinary companion builds one shared schematic. */
public final class SchematicCommands {
    private static final Map<String,Rotation> ROTATIONS=new LinkedHashMap<>();
    static{ROTATIONS.put("none",Rotation.NONE);ROTATIONS.put("cw90",Rotation.CLOCKWISE_90);ROTATIONS.put("180",Rotation.CLOCKWISE_180);ROTATIONS.put("ccw90",Rotation.COUNTERCLOCKWISE_90);}
    private SchematicCommands(){}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        var rotation=argument("rotation",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(ROTATIONS.keySet(),b))
            .executes(c->run(c,()->build(c,BlockPosArgument.getBlockPos(c,"pos"),rotation(StringArgumentType.getString(c,"rotation")))));
        var build=file().executes(c->run(c,()->build(c,null,Rotation.NONE)))
            .then(argument("pos",BlockPosArgument.blockPos()).executes(c->run(c,()->build(c,BlockPosArgument.getBlockPos(c,"pos"),Rotation.NONE))).then(rotation));
        root.then(literal("schematic")
            .then(literal("list").executes(c->run(c,()->list(c))))
            .then(literal("info").then(file().executes(c->run(c,()->info(c)))))
            .then(literal("build").then(build))
            .then(literal("plan").executes(c->run(c,()->{var job=manager(c).schematic;say(c,job==null?"No schematic build has been started":job.planReport());})))
            .then(literal("status").executes(c->run(c,()->{var job=manager(c).schematic;say(c,job==null?"No schematic build has been started":job.status());})))
            .then(literal("stop").executes(c->run(c,()->{
                var job=manager(c).schematic;
                if(job==null)throw new IllegalArgumentException("No schematic build is running");
                job.stop();
                for(var b:job.builders())if(b.tasks.current() instanceof SchematicBuildTask)b.tasks.stop();
            }))));
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> file(){
        return argument("file",StringArgumentType.string()).suggests((c,b)->SharedSuggestionProvider.suggest(SchematicFiles.list(c.getSource().getServer()).stream().map(n->n.substring(0,n.length()-6)),b));
    }
    private static Rotation rotation(String name){
        var r=ROTATIONS.get(name.toLowerCase(Locale.ROOT));
        if(r==null)throw new IllegalArgumentException("Rotation must be one of "+ROTATIONS.keySet());
        return r;
    }
    private static void list(CommandContext<CommandSourceStack> c){
        var names=SchematicFiles.list(c.getSource().getServer());
        if(names.isEmpty())say(c,"No .schem files found. Put them in "+SchematicFiles.directories(c.getSource().getServer()).getFirst());
        else say(c,names.size()+" schematic(s):\n"+String.join("\n",names));
    }
    /** File reading and planning run off the server thread; only the result is applied on it. */
    private static <T> void async(CommandContext<CommandSourceStack> c,java.util.function.Supplier<T> work,java.util.function.Consumer<T> apply){
        var source=c.getSource();var server=source.getServer();
        CompletableFuture.supplyAsync(work).whenCompleteAsync((result,error)->{
            if(error!=null){
                var cause=error instanceof java.util.concurrent.CompletionException&&error.getCause()!=null?error.getCause():error;
                source.sendFailure(Component.literal("Schematic: "+cause.getMessage()));return;
            }
            try{apply.accept(result);}catch(IllegalArgumentException|IllegalStateException e){source.sendFailure(Component.literal("Schematic: "+e.getMessage()));}
        },server);
    }
    private static Schematic read(java.nio.file.Path file){
        try{return Schematic.read(file);}catch(java.io.IOException e){throw new IllegalArgumentException("Cannot read "+file.getFileName()+": "+e.getMessage());}
    }
    private static void info(CommandContext<CommandSourceStack> c){
        var file=SchematicFiles.resolve(c.getSource().getServer(),StringArgumentType.getString(c,"file"));
        async(c,()->{var s=read(file);return SchematicPlan.create(s,BlockPos.ZERO,Rotation.NONE);},plan->{
            int w=plan.max().getX()+1,h=plan.max().getY()+1,l=plan.max().getZ()+1;
            say(c,plan.name()+": "+w+"×"+h+"×"+l+", "+plan.placements().size()+" blocks in "+h+" layers"+order(plan)+"\nMaterials: "+materials(plan)
                +(plan.skipped().isEmpty()?"":"\nNot buildable: "+plan.skipped()));
        });
    }
    private static String order(SchematicPlan plan){
        long fittings=plan.placements().stream().filter(SchematicPlan.Placement::fitting).count();
        long furnishings=plan.placements().stream().filter(SchematicPlan.Placement::furnishing).count();
        return "; then "+fittings+" fittings (ladders, doors, torches …); last "+furnishings+" furnishings (chests, crafting tables, furnaces …)";
    }
    private static String materials(SchematicPlan plan){
        return plan.materials().entrySet().stream().sorted(Map.Entry.<net.minecraft.world.item.Item,Integer>comparingByValue().reversed())
            .map(e->e.getValue()+" "+BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).collect(Collectors.joining(", "));
    }
    private static void build(CommandContext<CommandSourceStack> c,BlockPos pos,Rotation rotation) throws CommandSyntaxException {
        var source=c.getSource();var server=source.getServer();var level=source.getLevel();
        var file=SchematicFiles.resolve(server,StringArgumentType.getString(c,"file"));
        BlockPos feet=pos==null?source.getPlayerOrException().blockPosition():null;
        Direction facing=pos==null?source.getPlayerOrException().getDirection():null;
        async(c,()->{
            var schematic=read(file);
            boolean swap=rotation==Rotation.CLOCKWISE_90||rotation==Rotation.COUNTERCLOCKWISE_90;
            BlockPos origin=pos!=null?pos:defaultOrigin(feet,facing,swap?schematic.length():schematic.width(),swap?schematic.width():schematic.length());
            return SchematicPlan.create(schematic,origin,rotation);
        },plan->{
            if(level.isOutsideBuildHeight(plan.min())||level.isOutsideBuildHeight(plan.max()))throw new IllegalArgumentException("Schematic does not fit inside the build height at "+plan.min().toShortString());
            if(plan.placements().isEmpty())throw new IllegalArgumentException("Schematic has no buildable blocks");
            var manager=dev.companions.CompanionsMod.manager(server);
            var builders=manager.all().stream().filter(b->!b.flyer&&b.player.level()==level).toList();
            if(builders.isEmpty())throw new IllegalArgumentException("No ordinary companions in this dimension. Spawn some first: /bot spawn <name>");
            if(manager.schematic!=null)manager.schematic.stop();
            var job=new SchematicJob(level,plan,builders,text->source.sendSuccess(()->Component.literal(text),false));
            manager.schematic=job;
            for(var b:builders)b.tasks.set(new SchematicBuildTask(b,job),()->{});
            int layers=plan.max().getY()-plan.min().getY()+1;
            source.sendSuccess(()->Component.literal("Building "+plan.name()+" at "+plan.min().toShortString()+" → "+plan.max().toShortString()+" with "
                +builders.stream().map(b->b.player.getPlainTextName()).collect(Collectors.joining(", "))
                +"\n"+plan.placements().size()+" blocks, "+layers+" layers bottom to top"+order(plan)
                +"\nTemporary scaffold: "+job.scaffoldId+"\nMaterials (issued as needed): "+materials(plan)
                +(plan.skipped().isEmpty()?"":"\nNot buildable: "+plan.skipped())),false);
        });
    }
    /** Two blocks in front of the player, centred across their view, extending away from them. */
    public static BlockPos defaultOrigin(BlockPos feet,Direction facing,int sizeX,int sizeZ){
        BlockPos front=feet.relative(facing,2);
        int x=switch(facing){case EAST->front.getX();case WEST->front.getX()-(sizeX-1);default->front.getX()-(sizeX-1)/2;};
        int z=switch(facing){case SOUTH->front.getZ();case NORTH->front.getZ()-(sizeZ-1);default->front.getZ()-(sizeZ-1)/2;};
        return new BlockPos(x,feet.getY(),z);
    }
}
