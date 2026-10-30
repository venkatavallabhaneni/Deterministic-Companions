package dev.companions.task;

import dev.companions.player.CompanionController;
import dev.companions.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Predicate;

/** One resource block for the supply task; deliberately never starts another tool-acquisition loop. */
public final class GatherTask extends BaseTask {
    private final Predicate<BlockState> match;
    private final Set<BlockPos> rejected=new HashSet<>();
    private BlockSearch search;
    private BlockPos target;
    private String drop;
    private int ticks,collect,retryPasses;
    public GatherTask(CompanionController c,String resource,Predicate<BlockState> match){super(c,"GATHER "+resource);this.match=match;}
    public void tick() {
        if(++ticks>2400){finish(status+" — timed out");return;}
        if(collect>0){
            var entity=CollectTask.nearest(c,drop,12);
            if(entity!=null)c.navigation.target(entity.position(),0.5);
            if(--collect==0)finish(status+" complete");return;
        }
        if(target==null){
            if(search==null)search=new BlockSearch(c,c.player.blockPosition(),match,p->!rejected.contains(p)&&c.workAvailable(p)&&c.actions.mayBreak(p,true)&&c.inventory.hasHarvestTool(c.player.level().getBlockState(p)));
            search.tick();status="GATHER "+search.status();if(!search.done())return;
            var results=search.results();search=null;
            if(results.isEmpty()){
                if(++retryPasses<4){rejected.clear();search=null;status="GATHER — retrying bounded resource scan (pass "+retryPasses+"/3)";return;}
                finish(status+" — no reachable resources in loaded XYZ +/-"+BlockSearch.RADIUS+" radius");return;
            }
            target=results.getFirst();
        }
        if(!c.claimResource(target)){target=null;c.releaseResource();c.navigation.stop();return;}
        if(!c.player.level().hasChunkAt(target)||!match.test(c.player.level().getBlockState(target))){target=null;c.releaseResource();c.navigation.stop();return;}
        if(c.navigation.failed()){reject();return;}
        if(c.actions.reachable(target)&&c.workFrom(c.player.blockPosition())) {
            c.navigation.pause();
            drop=MineTask.dropFor(BuiltInRegistries.BLOCK.getKey(c.player.level().getBlockState(target).getBlock()).toString());
            var result=c.actions.breakBlock(target,true);
            if(result==WorldActionController.Result.SUCCESS){collect=60;target=null;}
            else if(result==WorldActionController.Result.BLOCKED)reject();
        }else if(ticks%20==0 && !InteractionPosition.approach(c,target,true))reject();
    }
    private void reject(){rejected.add(target);target=null;c.releaseResource();c.navigation.stop();if(rejected.size()>=32)finish(status+" — resource targets inaccessible");}
}
