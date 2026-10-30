package dev.companions.task;

import dev.companions.building.BuildPlan;
import dev.companions.navigation.path.Cell;
import dev.companions.player.CompanionController;
import dev.companions.world.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.companions.navigation.WorldNeighbors.*;

public final class BuildTask extends BaseTask {
    private final BuildPlan plan;
    private final List<Cell> pending;
    private Cell target;
    private int ticks,stalled,done;
    public BuildTask(CompanionController c,BuildPlan plan) {super(c,"BUILD "+plan.name());this.plan=plan;pending=new ArrayList<>(plan.placements());}
    @Override public void start() {
        for(Cell p:pending)if(!c.player.level().hasChunkAt(pos(p)) || c.player.level().isOutsideBuildHeight(pos(p))) {finish("BUILD: all plan targets must be within loaded chunks and build height");return;}
        long required=pending.stream().filter(p -> c.player.level().getBlockState(pos(p)).canBeReplaced()).count();
        int available=plan.material()==null?c.inventory.buildingCount():c.inventory.count(plan.material());
        if(available<required) {
            int missing=(int)Math.min(Integer.MAX_VALUE,(long)required-available);
            Item issued=plan.material()==null?Items.COBBLESTONE:BuiltInRegistries.ITEM.getOptional(Identifier.parse(plan.material())).orElse(Items.COBBLESTONE);
            if(!(issued instanceof BlockItem) || !c.inventory.grant(List.of(new ItemStack(issued,missing)))) {
                finish("BUILD: requires "+required+" blocks; inventory has "+available+" and automatic give could not fit");
                return;
            }
            status="BUILD "+plan.name()+" — issued "+missing+" "+BuiltInRegistries.ITEM.getKey(issued)+" via give";
        }
    }
    @Override public void tick() {
        if(complete)return;
        ticks++;status="BUILD "+plan.name()+" "+done+"/"+plan.placements().size();
        if(!c.config.placeBlocks) {finish(status+" — placement disabled");return;}
        if(pending.isEmpty()) {finish(status+" complete");return;}
        if(target==null) target=chooseNext();
        if(!c.player.level().hasChunkAt(pos(target))) {finish(status+" — target chunk unloaded");return;}
        var p=pos(target);var state=c.player.level().getBlockState(p);
        if(!state.canBeReplaced()) {
            boolean matching=plan.material()==null?BlockSafetyPolicy.disposable(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()):BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(plan.material());
            if(matching){advance();return;}
            if(plan.removals().contains(target) && c.actions.reachable(p)) {c.navigation.pause();if(c.actions.breakBlock(p)==WorldActionController.Result.BLOCKED)finish(status+" — removal denied");return;}
            finish(status+" — occupied target "+p.toShortString());return;
        }
        if(++stalled>600 || c.navigation.failed()) {finish(status+" — unreachable placement "+p.toShortString());return;}
        boolean pillar=plan.name().equals("pillar") && Math.abs(c.player.getX()-(target.x()+0.5))<0.4 && Math.abs(c.player.getZ()-(target.z()+0.5))<0.4;
        if(pillar) {c.navigation.pause();c.actions.jump();if(c.player.getY()<target.y()+1) return;}
        if(c.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p))<20.25) {
            if(c.actions.placeBlock(p,plan.material())==WorldActionController.Result.SUCCESS) {advance();return;}
        }
        if(ticks%20==1) {
            if(pillar) return;
            if(plan.name().equals("pillar")) c.navigation.target(net.minecraft.world.phys.Vec3.atBottomCenterOf(p),0.35);
            else if(!InteractionPosition.approach(c,p)) finish(status+" — no supported interaction position");
        }
    }
    private void advance() {pending.remove(target);target=null;done++;stalled=0;c.navigation.stop();}
    private Cell chooseNext() {
        if(plan.name().equals("pillar") || plan.name().equals("bridge"))return pending.getFirst();
        Cell best=pending.getFirst();double score=Double.POSITIVE_INFINITY;
        for(Cell candidate:pending.stream().limit(32).toList()) {
            var p=pos(candidate);double distance=c.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p));
            if(distance>=score || distance>20.25 || !c.actions.visible(p))continue;
            for(var direction:net.minecraft.core.Direction.values())if(!c.player.level().getBlockState(p.relative(direction)).getCollisionShape(c.player.level(),p.relative(direction)).isEmpty()) {best=candidate;score=distance;break;}
        }
        return best;
    }
}
