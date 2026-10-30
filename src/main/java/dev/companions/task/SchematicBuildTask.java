package dev.companions.task;

import dev.companions.player.CompanionController;
import dev.companions.schematic.*;
import dev.companions.world.WorldActionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/**
 * One companion's share of a {@link SchematicJob}. Each tick it either places
 * its claimed block, clears what occupies the cell, walks/pillars to a stand
 * position, removes scaffolding, or parks outside the footprint while it waits.
 * Every branch has a bounded timeout so the companion never idles on a block
 * it cannot finish; the job hands that block to someone else or skips it.
 */
public final class SchematicBuildTask extends BaseTask {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("companions");
    private static final int TARGET_TIMEOUT=900,NO_PROGRESS=200;
    private final SchematicJob job;
    private Integer target;
    private BlockPos removal,parking,stand;
    private final Set<BlockPos> failedStands=new HashSet<>();
    private long nextApproach,lastActivity,parkedAt;
    private int targetTicks,placeFailures,supports,approaches,settle,dwell,errors,unstick,placed,removed;
    private boolean supplied,left,parked;
    private Vec3 lastPosition;
    public SchematicBuildTask(CompanionController c,SchematicJob job){super(c,"SCHEMATIC "+job.plan.name());this.job=job;}

    @Override public void start(){
        c.inventory.constructionOverride=job.scaffoldId;
        c.construction.placed=job::recordScaffold;
        c.construction.protectedBlock=p->job.protects(c,p);
        c.construction.crowdCost=p->job.crowdCost(c,p);
        c.construction.placementCost=p->job.placementCost(c,p);
        c.construction.breakCost=job::breakCost;
        c.construction.broken=job::broken;
        c.inventory.swordDuty=false;
        lastActivity=c.player.level().getGameTime();
    }
    @Override public void cancel(){super.cancel();detach();}
    @Override protected void finish(String reason){super.finish(reason);detach();}
    private void detach(){
        if(left)return;left=true;
        if(target!=null&&job.owns(target,c))job.release(target);
        target=null;removal=null;
        c.inventory.constructionOverride=null;c.construction.reset();
        job.reserveStand(c,null);job.leave(c);
    }

    @Override public void tick(){
        if(complete)return;
        try{step();errors=0;}
        catch(RuntimeException e){
            // A builder must keep going: drop the current block and replan rather than letting
            // the manager stop the whole task. Persisting failures end only this builder.
            LOG.warn("Schematic builder {} recovered from an error",c.player.getPlainTextName(),e);
            fail("error: "+e.getMessage());c.navigation.stop();
            if(++errors>=40)finish("SCHEMATIC "+job.plan.name()+" — stopped after repeated errors: "+e.getMessage());
        }
    }
    private void step(){
        job.tick();
        if(job.stage()==SchematicJob.Stage.STOPPED){finish("SCHEMATIC "+job.plan.name()+" stopped — "+job.progress());return;}
        if(job.stage()==SchematicJob.Stage.DONE){finish("SCHEMATIC "+job.plan.name()+" complete — "+placed+" placed, "+removed+" scaffold removed by "+c.player.getPlainTextName());return;}
        long now=c.player.level().getGameTime();
        if(!supplied){supplied=true;supplyBasics();}
        else if(now%100==0)keepScaffoldStock();
        if(!c.config.placeBlocks){status="SCHEMATIC waiting — placement disabled (/bot config "+c.player.getPlainTextName()+" placeBlocks on)";c.navigation.stop();return;}
        watchdog(now);
        // Settling steps are one tick each; never leave the movement input held.
        if(settle>0)c.actions.halt();
        switch(job.stage()){
            case CLEARING->remove(now,true);
            case PLANNING->idle(now,"planning the build");
            case BUILDING->{
                if(removal!=null){removal=null;resetApproach();}
                build(now);
            }
            default->{
                if(target!=null)dropTarget(false);
                remove(now,false);
            }
        }
    }

    // ---- placing ---------------------------------------------------------------
    private void build(long now){
        if(target!=null&&!job.owns(target,c)){target=null;resetApproach();}
        if(target==null){
            target=job.claimNext(c);
            if(target==null){String bench=job.benchNote(c);idle(now,bench!=null?bench:"waiting for the current layer");return;}
            resetApproach();parked=false;
        }
        job.touch(target);
        var p=job.placement(target);
        BlockPos pos=p.pos();
        var level=c.player.level();
        status="SCHEMATIC "+job.plan.name()+" layer "+(pos.getY()-job.plan.min().getY()+1)+": "+BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).getPath()+" @ "+pos.toShortString()+" — "+job.progress();
        if(!level.hasChunkAt(pos)){fail("chunk not loaded");return;}
        if(PlacementRules.matches(level.getBlockState(pos),p.state())){job.complete(target,false);target=null;return;}
        // The right block facing or paired the wrong way (e.g. a chest joined to its neighbour):
        // fix it in place. A double slab still needs its second slab, so that one is rebuilt.
        if(level.getBlockState(pos).getBlock()==p.state().getBlock()&&PlacementRules.itemCount(p.state())==1){
            if(SchematicPlacer.realign(c,pos,p.state())){job.complete(target,false);activity(now);target=null;return;}
            approach(now,pos,Reach.CENTRE);
            return;
        }
        for(BlockPos cell:p.partner()==null?List.of(pos):List.of(pos,p.partner())){
            BlockState there=level.getBlockState(cell);
            if(there.canBeReplaced())continue;
            if(!job.scaffoldLike(there)&&!c.actions.mayBreak(cell,true)){job.skip(target);target=null;status+=" — occupied by a protected block, skipped";return;}
            // Someone is standing on this scaffold column: come back to it later.
            if(job.isScaffold(cell)&&job.supportsOther(c,cell)){job.defer(target,60);target=null;return;}
            clear(now,cell);return;
        }
        String id=BuiltInRegistries.ITEM.getKey(p.item()).toString();
        if(c.inventory.count(id)<p.count()){
            int amount=Math.max(p.count(),job.supplyAmount(p.item()));
            if(!c.inventory.grant(List.of(new ItemStack(p.item(),amount)))&&!c.inventory.grant(List.of(new ItemStack(p.item(),p.count())))){
                if(!freeSpace()){fail("inventory full; cannot take "+id);}
                return;
            }
        }
        // Carry the block this companion is on its way to place. Navigation swaps in a scaffold
        // block only for the instant it pillars or bridges; the next tick puts this back.
        hold(p.item());
        // A block that keeps failing the normal way (no clickable face from any reachable spot,
        // placement refused ...) is escalated: stand anywhere with the cell itself in view and
        // set it directly, still consuming the real item.
        boolean anyhow=job.schedule.attempts(target)>=3||job.schedule.onSecondPass(target)||job.plannedDirect(target);
        if(anyhow&&SchematicPlacer.inReach(c,pos)&&(c.actions.visible(pos)||enclosed(pos))){
            if(SchematicPlacer.place(c,pos,p.state(),p.item())==SchematicPlacer.Result.PLACED||SchematicPlacer.force(c,pos,p.state(),p.item())){
                job.complete(target,true);placed++;activity(now);target=null;job.reserveStand(c,null);return;
            }
        }
        if(!anyhow&&(SchematicPlacer.inReach(c,pos)||SchematicPlacer.hasFace(c,pos,p.state()))){
            switch(SchematicPlacer.place(c,pos,p.state(),p.item())){
                case PLACED->{job.complete(target,false);placed++;activity(now);target=null;job.reserveStand(c,null);return;}
                case OBSTRUCTED->{
                    var box=new AABB(pos);
                    if(c.player.getBoundingBox().intersects(box)||p.partner()!=null&&c.player.getBoundingBox().intersects(new AABB(p.partner()))){nextApproach=0;break;}
                    job.defer(target,40);target=null;return;
                }
                case NO_FACE->{
                    if(!solidNeighbor(pos)){
                        if(supports<6&&placeSupport(pos)){supports++;activity(now);return;}
                        if(supports>=6&&SchematicPlacer.force(c,pos,p.state(),p.item())){job.complete(target,true);placed++;activity(now);target=null;return;}
                    }
                    if(approaches>=4&&SchematicPlacer.force(c,pos,p.state(),p.item())){job.complete(target,true);placed++;activity(now);target=null;return;}
                }
                case FAILED->{
                    if(++placeFailures>=3){
                        if(SchematicPlacer.force(c,pos,p.state(),p.item())){job.complete(target,true);placed++;activity(now);target=null;}
                        else fail("placement refused three times");
                        return;
                    }
                    return;
                }
                case NO_ITEM->{return;}
            }
        }
        approach(now,pos,anyhow?Reach.CENTRE:Reach.FACE);
    }
    /** Clears whatever occupies a target cell, never while standing on it over a long drop. */
    private void clear(long now,BlockPos cell){
        if(c.actions.reachable(cell)){
            if(c.actions.mayBreak(cell,true)){
                c.navigation.pause();
                var r=c.actions.breakBlock(cell,true);
                if(r==WorldActionController.Result.BLOCKED)fail("could not break the block in the way");else activity(now);
                if(r==WorldActionController.Result.SUCCESS)job.scaffoldRemoved(cell);
                return;
            }
            // Usually: standing on it with nothing safe below. Step off and break it from the side.
            if(!cell.equals(c.player.blockPosition().below())){fail("the block in the way may not be broken");return;}
        }
        approach(now,cell,Reach.CENTRE);
    }
    private boolean solidNeighbor(BlockPos pos){
        var level=c.player.level();
        for(Direction d:Direction.values())if(!level.getBlockState(pos.relative(d)).getCollisionShape(level,pos.relative(d)).isEmpty())return true;
        return false;
    }
    /** Builds a temporary scaffold column (or side support) under a floating block, nearest cell first. */
    private boolean placeSupport(BlockPos pos){
        var level=c.player.level();
        var candidates=new ArrayList<BlockPos>();
        for(int d=1;d<=6;d++)candidates.add(pos.below(d));
        for(Direction h:Direction.Plane.HORIZONTAL)candidates.add(pos.relative(h));
        for(BlockPos cell:candidates){
            if(!level.getBlockState(cell).canBeReplaced()||job.pendingNear(cell)&&!cell.equals(pos.below()))continue;
            if(c.player.getBoundingBox().intersects(new AABB(cell)))continue;
            if(c.actions.placeBlock(cell,job.scaffoldId)==WorldActionController.Result.SUCCESS){job.recordScaffold(cell);return true;}
            if(cell.getY()<pos.getY()&&!level.getBlockState(cell.below()).canBeReplaced())break;
        }
        return false;
    }

    // ---- clearing and scaffold removal ------------------------------------------
    /**
     * Breaks one claimed block: before construction anything in the way inside
     * the build area and its margin (top layer first), after construction the
     * temporary scaffolding (highest first).
     */
    private void remove(long now,boolean clearing){
        if(removal==null){
            removal=clearing?job.claimClear(c):job.claimScaffold(c);
            if(removal==null){idle(now,clearing?"waiting while the area is cleared":"waiting while other builders remove scaffolding");return;}
            resetApproach();parked=false;
        }
        job.touchScaffold(removal);
        var level=c.player.level();
        status="SCHEMATIC "+job.plan.name()+(clearing?" clearing ":" removing scaffold ")+BuiltInRegistries.BLOCK.getKey(level.getBlockState(removal).getBlock()).getPath()+" @ "+removal.toShortString()+" — "+job.progress();
        BlockState state=level.getBlockState(removal);
        if(clearing?!job.needsClearing(removal,state):!job.scaffoldLike(state)){job.scaffoldRemoved(removal);removal=null;return;}
        if(!clearing&&level.getBlockState(removal.above()).getBlock() instanceof FallingBlock&&job.plan.contains(removal.above())){job.scaffoldLeftover(removal);removal=null;return;}
        // Walk over with the right tool already in hand (shovel for dirt, pickaxe for stone ...).
        if(targetTicks%20==0)c.inventory.tool(state);
        boolean under=removal.equals(c.player.blockPosition().below());
        if(c.actions.reachable(removal)&&(!under||c.actions.mayBreak(removal,true))){
            c.navigation.pause();
            var r=c.actions.breakBlock(removal,true);
            if(r==WorldActionController.Result.SUCCESS){job.scaffoldRemoved(removal);removed++;activity(now);removal=null;job.reserveStand(c,null);}
            else if(r==WorldActionController.Result.BLOCKED){job.scaffoldFailed(removal);removal=null;}
            else activity(now);
            return;
        }
        approach(now,removal,Reach.ON_TOP);
    }

    // ---- movement --------------------------------------------------------------
    /**
     * Walks (or pillars) to one chosen stand and keeps it until it is reached or
     * the route fails, so a half-built pillar is never abandoned for another
     * spot. At the stand the companion settles onto the block centre, so its
     * body cannot overlap the cell it is about to fill; a stand that still does
     * not work is remembered and a different one is chosen.
     */
    /** FACE: click a neighbour's face (normal placement). CENTRE: see the cell itself (breaking, realigning,
     *  escalated placement), never from on top of it. ON_TOP: removal, may also dig it out from above. */
    private enum Reach{FACE,CENTRE,ON_TOP}
    private void approach(long now,BlockPos cell,Reach reach){
        if(++targetTicks>TARGET_TIMEOUT){fail("timed out reaching it",true);return;}
        if(stand!=null){
            Vec3 center=Vec3.atBottomCenterOf(stand);
            double dx=c.player.getX()-center.x,dz=c.player.getZ()-center.z;
            boolean at=dx*dx+dz*dz<=0.36&&Math.abs(c.player.getY()-center.y)<0.75;
            if(at){
                if(dx*dx+dz*dz>0.01&&settle<20){
                    // Nudge onto the block centre with ordinary physics. No crouch: it lowers the eyes,
                    // and the stand was chosen for standing eye height.
                    settle++;c.navigation.pause();
                    var v=c.player.getDeltaMovement();
                    c.player.setDeltaMovement(-dx*0.35,v.y,-dz*0.35);
                    return;
                }
                // Stand still for half a second at full eye height so placing/breaking (checked
                // every tick before this) sees exactly what the stand was chosen for.
                if(++dwell<10){c.navigation.pause();c.actions.halt();return;}
                failedStands.add(stand);stand=null;approaches++;
            }else if(c.navigation.failed()){
                // No route to that spot even with scaffolding: from now on it may break through finished blocks.
                failedStands.add(stand);stand=null;approaches++;
                breakThrough();
            }else if(c.navigation.searching()||c.navigation.followingPath()||now<nextApproach){
                return;
            }else{
                // Navigation is idle (paused for a break, or between replans): resume toward the same stand.
                c.navigation.target(center,0.45);nextApproach=now+40;
                return;
            }
        }
        if(approaches>6){fail("no working spot after "+failedStands.size()+" tries",true);return;}
        if(approaches>=2)breakThrough();
        settle=0;dwell=0;c.actions.halt();
        var order=reach==Reach.FACE?PlacementRules.supportOrder(job.placement(target).state()):null;
        // Escalation, first step: once a spot failed (or when sealing a gap), prefer spots outside the
        // building's footprint, reached by scaffolding up the outside, over working from inside it.
        boolean outside=approaches>=1||target!=null&&job.sealing(target);
        java.util.function.ToDoubleFunction<BlockPos> cost=outside?b->job.standCost(b)+(job.plan.overFootprint(b)?10:0):job::standCost;
        // Follow the plan first: the stand worked out before building (a shared scaffold tower, the
        // top of a wall ...). Live searching takes over once that spot has failed for this block.
        BlockPos planned=target!=null&&reach!=Reach.ON_TOP&&cell.equals(job.placement(target).pos())?job.plannedStand(target):null;
        if(planned!=null&&!failedStands.contains(planned)&&!job.standTaken(c,planned))stand=planned;
        else stand=StandFinder.find(StandFinder.view(c),cell,c.player.position(),c.player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING),order,reach==Reach.ON_TOP,b->job.standTaken(c,b),cost,failedStands);
        if(stand==null){approaches++;nextApproach=now+20;if(approaches>=3)fail(reach==Reach.FACE?"no spot can click a face of it":"no spot can see it",true);return;}
        job.reserveStand(c,stand);
        c.navigation.stop();
        c.navigation.target(Vec3.atBottomCenterOf(stand),0.45);
        nextApproach=now+40;
    }
    /**
     * Escalation, second step: routes may now break finished schematic blocks (still at a high cost, so
     * walking or scaffolding around stays preferred). Every block broken this way is sealed at the end.
     */
    private void breakThrough(){
        if(job.breaching(c))return;
        job.allowBreach(c);
        status="SCHEMATIC "+job.plan.name()+" — can't get there around the building; may break through and seal it later";
    }
    private boolean enclosed(BlockPos pos){
        var level=c.player.level();
        for(Direction d:Direction.values())if(level.getBlockState(pos.relative(d)).getCollisionShape(level,pos.relative(d)).isEmpty())return false;
        return true;
    }
    private void idle(long now,String why){
        status="SCHEMATIC "+job.plan.name()+" — "+why+" — "+job.progress();
        job.reserveStand(c,null);
        BlockPos feet=c.player.blockPosition();
        boolean inTheWay=job.pendingNear(feet)||job.pendingNear(feet.above())||job.isScaffold(feet.below())&&job.stage()==SchematicJob.Stage.CLEANUP;
        if(inTheWay&&(parking==null||now>=parkedAt+100)){
            parking=StandFinder.park(StandFinder.view(c),job.plan,c.player.position(),b->job.pendingNear(b)||job.standTaken(c,b));parkedAt=now;
            if(parking!=null){c.navigation.target(Vec3.atBottomCenterOf(parking),0.6);parked=false;return;}
        }
        if(parking!=null&&!c.navigation.arrived()&&(c.navigation.searching()||c.navigation.followingPath()))return;
        if(!parked){c.navigation.stop();c.inventory.stowHeld();parked=true;parking=null;}
    }
    private void hold(Item item){
        if(c.player.getMainHandItem().is(item))return;
        int slot=c.inventory.find(BuiltInRegistries.ITEM.getKey(item).toString());
        if(slot>=0)c.inventory.select(slot);
    }
    private void resetApproach(){targetTicks=0;placeFailures=0;supports=0;approaches=0;nextApproach=0;settle=0;dwell=0;stand=null;failedStands.clear();c.navigation.stop();}
    /** Hands the current block back as a failed attempt, recording why for /bot schematic status. */
    private void fail(String why){fail(why,false);}
    /** @param unreachable the companion could not get to the block (counts toward benching it for the layer) */
    private void fail(String why,boolean unreachable){
        if(target!=null&&job.owns(target,c))job.fail(target,why,unreachable);
        else if(removal!=null)job.scaffoldFailed(removal);
        target=null;removal=null;job.reserveStand(c,null);
        targetTicks=0;placeFailures=0;supports=0;approaches=0;nextApproach=0;settle=0;dwell=0;stand=null;failedStands.clear();
    }
    private void dropTarget(boolean failed){
        if(target!=null&&job.owns(target,c)){if(failed)job.fail(target,"interrupted");else job.release(target);}
        if(removal!=null&&failed)job.scaffoldFailed(removal);
        target=null;removal=null;job.reserveStand(c,null);
        targetTicks=0;placeFailures=0;supports=0;approaches=0;nextApproach=0;settle=0;dwell=0;stand=null;failedStands.clear();
    }
    private void activity(long now){lastActivity=now;unstick=0;c.construction.maxFall=dev.companions.building.ConstructionPolicy.SAFE_FALL;job.progressed();}
    /**
     * Per-builder anti-freeze: if the companion has neither moved nor finished
     * any block for ten seconds while it holds work, hand the work back, jump,
     * and replan. A second consecutive stall lets it break out through finished
     * blocks (the job rebuilds the gap) in case it walled itself in.
     */
    private void watchdog(long now){
        Vec3 position=c.player.position();
        boolean working=target!=null||removal!=null;
        if(lastPosition==null||position.distanceToSqr(lastPosition)>0.36||!working){lastPosition=position;lastActivity=now;return;}
        if(now-lastActivity<NO_PROGRESS)return;
        lastActivity=now;unstick++;
        fail("no progress for "+NO_PROGRESS/20+"s",true);c.navigation.stop();c.actions.jump();
        if(unstick>=2){
            // Stranded (walled in, or on a structure whose scaffold is gone): break out through
            // finished blocks, which the job rebuilds, and accept a longer drop if healthy enough.
            breakThrough();
            if(c.player.getHealth()>=12)c.construction.maxFall=6;
            status="SCHEMATIC — stuck; breaking out and replanning";
        }
        else status="SCHEMATIC — no progress for "+NO_PROGRESS/20+"s; replanning";
    }

    // ---- supplies --------------------------------------------------------------
    private void supplyBasics(){
        var notes=new ArrayList<String>();
        give(notes,ItemTags.PICKAXES,Items.DIAMOND_PICKAXE);
        give(notes,ItemTags.SHOVELS,Items.DIAMOND_SHOVEL);
        give(notes,ItemTags.AXES,Items.DIAMOND_AXE);
        boolean food=false;
        for(int i=0;i<36;i++)if(c.player.getInventory().getItem(i).has(DataComponents.FOOD)){food=true;break;}
        if(!food&&c.inventory.grant(List.of(new ItemStack(Items.BREAD,16))))notes.add("16 bread");
        keepScaffoldStock();
        if(!notes.isEmpty())status="SCHEMATIC "+job.plan.name()+" — issued "+String.join(", ",notes);
    }
    private void give(List<String> notes,TagKey<Item> kind,Item tool){
        for(int i=0;i<36;i++)if(c.player.getInventory().getItem(i).is(kind))return;
        if(c.inventory.grant(List.of(new ItemStack(tool))))notes.add(BuiltInRegistries.ITEM.getKey(tool).getPath());
    }
    private void keepScaffoldStock(){
        int have=c.inventory.count(job.scaffoldId);
        if(have<32&&!c.inventory.grant(List.of(new ItemStack(job.scaffoldItem,64-have)))&&freeSpace())
            c.inventory.grant(List.of(new ItemStack(job.scaffoldItem,64-have)));
    }
    /** Drops picked-up blocks the build does not need (cleared logs, stone, dirt, saplings ...), never tools, food, armour or schematic materials. */
    private boolean freeSpace(){
        boolean freed=false;int scaffold=0;
        for(int i=0;i<36;i++){
            var s=c.player.getInventory().getItem(i);
            if(s.isEmpty()||i==c.player.getInventory().getSelectedSlot())continue;
            String id=BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            if(s.is(job.scaffoldItem)){if((scaffold+=s.getCount())<=128)continue;}
            else if(!(s.getItem() instanceof BlockItem)||job.needed(s.getItem()))continue;
            c.player.drop(c.player.getInventory().removeItemNoUpdate(i),false,false);freed=true;
        }
        if(freed)c.player.inventoryMenu.broadcastChanges();
        return freed;
    }
}
