package dev.companions.task;

import dev.companions.player.CompanionController;
import dev.companions.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import java.util.*;

public final class MineTask extends BaseTask {
    private final Block block;
    private final String id,drop;
    private final int amount,initial,initialBlock,automaticDropAtStart;
    private final ArrayDeque<BlockPos> vein=new ArrayDeque<>();
    private final Set<BlockPos> rejected=new HashSet<>();
    private final List<BlockPos> candidates=new ArrayList<>();
    private BlockPos target,scanCenter;
    private BlockSearch search;
    private int targetTicks,collectTicks;
    private int coordinationWait,rescanDelay;
    private int targetScanRetries;
    private boolean constructionBlocksChecked;
    public MineTask(CompanionController c,Block block,int amount) {
        super(c,"MINING");this.block=block;this.amount=amount;id=BuiltInRegistries.BLOCK.getKey(block).toString();
        drop=dropFor(id);initial=c.inventory.count(drop);initialBlock=c.inventory.count(id);automaticDropAtStart=c.inventory.automaticGranted(drop);scanCenter=c.player.blockPosition();
        status="MINING "+id+" 0/"+amount;
        var state=block.defaultBlockState();
        if(!c.config.breakBlocks)throw new IllegalArgumentException("Block breaking is disabled for "+c.player.getPlainTextName());
        if(state.isAir() || !state.getFluidState().isEmpty())throw new IllegalArgumentException("Cannot mine air or fluid: "+id);
        if(!BlockSafetyPolicy.mayBreak(id,state.hasBlockEntity(),state.getDestroySpeed(c.player.level(),c.player.blockPosition()),true))
            throw new IllegalArgumentException("Mining target is protected or unbreakable: "+id);
    }
    public static String dropFor(String id) {
        String key=id.replace("deepslate_","");
        return switch(key) {
            case "minecraft:iron_ore"->"minecraft:raw_iron";case "minecraft:copper_ore"->"minecraft:raw_copper";case "minecraft:gold_ore"->"minecraft:raw_gold";
            case "minecraft:coal_ore"->"minecraft:coal";case "minecraft:diamond_ore"->"minecraft:diamond";case "minecraft:emerald_ore"->"minecraft:emerald";
            case "minecraft:lapis_ore"->"minecraft:lapis_lazuli";case "minecraft:redstone_ore"->"minecraft:redstone";case "minecraft:nether_quartz_ore"->"minecraft:quartz";
            case "minecraft:nether_gold_ore"->"minecraft:gold_nugget";case "minecraft:stone"->"minecraft:cobblestone";default->id;
        };
    }
    @Override public void tick() {
        int automaticDropDuringTask=Math.max(0,c.inventory.automaticGranted(drop)-automaticDropAtStart);
        int progress=Math.max(0,c.inventory.count(drop)-initial-automaticDropDuringTask)+(id.equals(drop)?0:Math.max(0,c.inventory.count(id)-initialBlock));
        status="MINING "+id+" "+progress+"/"+amount;
        if(progress>=amount){finish(status+" complete");return;}
        if(!constructionBlocksChecked){
            constructionBlocksChecked=true;
            if(c.config.bridge&&c.config.placeBlocks&&c.inventory.buildingCount()<8 && c.inventory.grant(List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64))))
                status+=" — supplied 64 cobblestone for safe mining access";
        }
        if(!dev.companions.crafting.ToolRequirements.equippedOrAvailable(c,block.defaultBlockState())){
            var tool=dev.companions.crafting.ToolRequirements.cheapest(block.defaultBlockState());
            if(tool==null){finish(status+" — no supported harvest tool");return;}
            if(!c.inventory.grant(List.of(new net.minecraft.world.item.ItemStack(tool)))){
                finish(status+" — inventory full; could not issue "+BuiltInRegistries.ITEM.getKey(tool));return;
            }
            status+=" — issued "+BuiltInRegistries.ITEM.getKey(tool)+" via give";
            c.inventory.tool(block.defaultBlockState());
        }
        if(!c.config.breakBlocks){finish(status+" — breaking disabled");return;}
        if(collectTicks>0) {
            collectTicks--;
            var entity=CollectTask.nearest(c,drop,12);
            if(entity==null && !drop.equals(id))entity=CollectTask.nearest(c,id,12);
            if(entity!=null) {if(!c.inventory.canAccept(entity.getItem())){finish(status+" — inventory full");return;}c.navigation.target(entity.position(),0.5);}
            else if(collectTicks<40) {collectTicks=0;c.navigation.stop();}
            return;
        }
        if(target!=null) {
            if(!c.claimResource(target)){target=null;c.releaseResource();c.navigation.stop();return;}
            if(!c.player.level().hasChunkAt(target)) {finish(status+" — target chunk unloaded");return;}
            if(!c.player.level().getBlockState(target).is(block)) {target=null;c.releaseResource();c.navigation.stop();return;}
            if(++targetTicks>4000 || c.navigation.failed()) {reject();return;}
            if(c.actions.reachable(target)&&c.workFrom(c.player.blockPosition())) {
                c.navigation.pause(); c.inventory.tool(c.player.level().getBlockState(target));
                if(c.player.level().getBlockState(target).requiresCorrectToolForDrops() && !c.player.hasCorrectToolForDrops(c.player.level().getBlockState(target))) {finish(status+" — no suitable usable tool remains in inventory");return;}
                var result=c.actions.breakBlock(target,true);
                if(result==WorldActionController.Result.SUCCESS) {
                    for(Direction d:Direction.values()) {BlockPos neighbor=target.relative(d);if(c.player.level().hasChunkAt(neighbor)&&c.player.level().getBlockState(neighbor).is(block)&&vein.size()<64) vein.add(neighbor);}
                    target=null;collectTicks=100;
                } else if(result==WorldActionController.Result.BLOCKED) reject();
            } else if(targetTicks%20==1 && !InteractionPosition.approach(c,target,true)) reject();
            return;
        }
        while(!vein.isEmpty()) {BlockPos p=vein.remove();if(valid(p)){choose(p);return;}}
        while(!candidates.isEmpty()){BlockPos p=candidates.removeFirst();if(valid(p)){choose(p);return;}}
        if(rescanDelay>0){rescanDelay--;status+=" — waiting for a free mining work zone";return;}
        if(search==null)search=new BlockSearch(c,scanCenter,s->s.is(block),this::valid);
        search.tick();
        status+=" — "+search.status();
        if(search.done()){
            candidates.addAll(search.results());search=null;
            if(candidates.isEmpty()){
                if(coordinationWait<12000&&c.resources.hasOtherWorkers(c.player.getUUID(),c.player.level().dimension().identifier().toString(),c.player.level().getGameTime())){
                    c.releaseResource();c.navigation.stop();rescanDelay=100;coordinationWait+=100;
                }else if(++targetScanRetries<4){
                    rejected.clear();search=null;coordinationWait=0;rescanDelay=100;c.navigation.stop();
                    status+=" — retrying bounded scan after inaccessible targets (pass "+targetScanRetries+"/3)";
                }else finish(status+" — no safe matching targets within XYZ +/-"+BlockSearch.RADIUS+" loaded blocks");
            }
        }
    }
    private boolean valid(BlockPos p) {return BlockSearch.inRange(scanCenter,p)&&!rejected.contains(p)&&c.workAvailable(p)&&c.player.level().hasChunkAt(p)&&c.player.level().getBlockState(p).is(block)&&c.actions.mayBreak(p,true);}
    private void choose(BlockPos p) {if(!c.claimResource(p))return;target=p.immutable();targetTicks=0;coordinationWait=0;c.navigation.stop();}
    private void reject() {
        if(target!=null&&rejected.size()<512)rejected.add(target);
        target=null;c.releaseResource();c.navigation.stop();
        status="MINING "+id+" — target route failed; selecting another reachable block";
        if(rejected.size()>=512){rejected.clear();search=null;candidates.clear();rescanDelay=100;targetScanRetries++;}
    }
}
