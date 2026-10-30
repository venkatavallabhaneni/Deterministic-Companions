package dev.companions.navigation;

import dev.companions.config.CompanionConfig;
import dev.companions.player.CompanionController;
import dev.companions.navigation.path.*;
import dev.companions.navigation.action.PathAction;
import dev.companions.world.WorldActionController.Result;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static dev.companions.navigation.WorldNeighbors.*;

public final class NavigationController {
    private final CompanionController c;
    public final WorldNeighbors world;
    private final Deque<PathEdge> path=new ArrayDeque<>();
    private BoundedAStar search;
    private Cell goal;
    private double tolerance;
    private long nextSearch;
    private long nextTrackingUpdate;
    private boolean extending;
    private Vec3 last=Vec3.ZERO;
    private int stuck,failures;
    private Cell pendingPlacement;
    private Cell placementSource;
    private int lastMaterials=-1;
    private long retryAt;
    private record EdgeKey(Cell destination,PathAction action){}
    private final Map<EdgeKey,Long> blockedEdges=new HashMap<>();
    private int placementTicks;
    private String status="IDLE";
    public NavigationController(CompanionController c) {this.c=c;world=new WorldNeighbors(c);}
    public void stop() {path.clear();search=null;goal=null;stuck=failures=0;pendingPlacement=null;placementTicks=0;blockedEdges.clear();status="IDLE";c.actions.cancelBreak();c.actions.halt();}
    public void pause() {path.clear();search=null;goal=null;pendingPlacement=null;placementTicks=0;status="PAUSED";c.actions.halt();}
    public void target(Vec3 point,double tolerance) {
        Cell to=new Cell((int)Math.floor(point.x),(int)Math.floor(point.y),(int)Math.floor(point.z));
        double retarget=Math.max(0.5,tolerance);
        if(goal==null || goal.distanceSquared(to)>retarget*retarget || this.tolerance!=tolerance) setTarget(to,tolerance);
    }
    /** Tracks a moving entity without discarding an in-progress bounded A* search for each step. */
    public void targetTracking(Vec3 point,double tolerance) {
        Cell to=new Cell((int)Math.floor(point.x),(int)Math.floor(point.y),(int)Math.floor(point.z));
        if(goal==null || this.tolerance!=tolerance){setTarget(to,tolerance);return;}
        long now=c.player.level().getGameTime();
        if(now<nextTrackingUpdate || !TrackingRetargetPolicy.shouldRetarget(
            goal.distanceSquared(to),search!=null,!path.isEmpty(),arrived(),tolerance))return;
        nextTrackingUpdate=now+10;
        if(arrived())path.clear();
        goal=to;failures=0;nextSearch=0;
        // Keep the next few validated actions. Repair only the route tail from
        // their endpoint while the companion continues walking that prefix.
        if(!path.isEmpty()) {
            RouteReuse.keepPrefix(path,6);
            startSearch(path.getLast().destination());extending=true;
        }
    }
    private void setTarget(Cell to,double tolerance){path.clear();search=null;extending=false;nextTrackingUpdate=0;goal=to;this.tolerance=tolerance;failures=0;nextSearch=0;pendingPlacement=null;placementTicks=0;c.actions.cancelBreak();}
    private void startSearch(Cell start){world.newSearch();search=new BoundedAStar(start,goal,tolerance,CompanionConfig.SEARCH_RADIUS,CompanionConfig.MAX_SEARCH_NODES,
        (from,via)->world.edges(from,via).stream().filter(e->!blockedEdges.containsKey(new EdgeKey(e.destination(),e.action())))
            .map(this::crowded).toList());}
    /** Other companions' bodies and reserved work spots cost a detour, so routes avoid them when they can. */
    private PathEdge crowded(PathEdge e){
        double extra=c.construction.crowdCost.applyAsDouble(e.destination());
        if(e.placement()!=null)extra+=c.construction.placementCost.applyAsDouble(e.placement());
        return extra<=0?e:new PathEdge(e.destination(),e.action(),e.breaks(),e.placement(),e.extraCost()+extra);
    }
    public boolean arrived() {return goal!=null && c.player.position().distanceToSqr(new Vec3(goal.x()+0.5,goal.y(),goal.z()+0.5))<=Math.max(0.45,tolerance)*Math.max(0.45,tolerance);}
    public boolean failed() {return failures>=5;}
    public boolean searching() {return search!=null;}
    public boolean followingPath() {return !path.isEmpty();}
    public int remaining() {return path.size();}
    public String status() {return status;}
    private void invalidate() {invalidate(true);}
    private void invalidate(boolean rememberEdge) {
        long now=c.player.level().getGameTime();
        if(rememberEdge&&!path.isEmpty()){
            if(blockedEdges.size()>=64)blockedEdges.clear();
            var edge=path.peek();blockedEdges.put(new EdgeKey(edge.destination(),edge.action()),now+200);
        }
        path.clear();search=null;pendingPlacement=null;placementTicks=0;nextSearch=now+10;retryAt=nextSearch+90;c.actions.cancelBreak();c.actions.halt();failures++;
    }
    /** Doors this companion opened, closed again once it is clear of them. */
    private final Map<net.minecraft.core.BlockPos,Long> opened=new HashMap<>();
    private void closeDoorsBehind(long now) {
        if(opened.isEmpty())return;
        var level=c.player.level();
        for(var it=opened.entrySet().iterator();it.hasNext();) {
            var e=it.next();var door=e.getKey();
            var state=level.getBlockState(door);
            if(!level.hasChunkAt(door)||!Passages.passage(state)||!Passages.open(state)||now-e.getValue()>200){it.remove();continue;}
            var cell=new net.minecraft.world.phys.AABB(door).expandTowards(0,1,0);
            if(c.player.getBoundingBox().intersects(cell.inflate(0.35)))continue;
            // Never shut a door on a player, companion or pet that is still passing through.
            if(!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,cell).isEmpty())continue;
            if(!c.actions.reachable(door))continue;
            if(c.actions.useBlock(door))it.remove();
        }
    }
    public void tick() {
        long now=c.player.level().getGameTime();
        closeDoorsBehind(now);
        if(goal==null) return;
        int materials=c.inventory.buildingCount();
        if(materials>lastMaterials&&lastMaterials>=0){blockedEdges.clear();failures=0;nextSearch=0;}
        lastMaterials=materials;
        blockedEdges.values().removeIf(until->until<=now);
        if(arrived()) {c.actions.halt();status="ARRIVED";return;}
        if(failed()) {c.actions.halt();status="BLOCKED: no safe route";if(now>=retryAt){failures=0;search=null;path.clear();nextSearch=now;}else return;}
        if(cell(c.player.blockPosition()).distance(goal)>c.config.maxDistance) {c.actions.halt();status="Target exceeds maxDistance";failures=5;return;}
        if(path.isEmpty()&&search==null&&now>=nextSearch){startSearch(cell(c.player.blockPosition()));extending=false;}
        if(search!=null) {
            world.beginScan();
            try{search.step(CompanionConfig.NODES_PER_TICK);}finally{world.endScan();}
            status="SEARCH "+search.explored();
            if(search.done()) {
                boolean repairing=extending;
                path.addAll(search.path());search=null;extending=false;nextSearch=now+10;
                if(path.isEmpty()){failures++;retryAt=now+100;}
                if(!repairing){last=c.player.position();stuck=0;}
            }
        }
        if(path.isEmpty()){c.actions.halt();return;}
        PathEdge edge=path.peek(); Cell to=edge.destination();
        if(world.hazard(to)||world.hazard(to.add(0,-1,0))) {invalidate();return;}
        for(Cell b:edge.breaks()) if(TraversalGeometry.blocksMovement(c.player.level(),pos(b))) {
            Result r=c.actions.breakBlock(pos(b)); if(r==Result.BLOCKED) invalidate(); return;
        }
        // Open any closed door/gate panel between this cell and the next before walking.
        for(Cell door:List.of(cell(c.player.blockPosition()),to)) {
            var state=c.player.level().getBlockState(pos(door));
            if(!Passages.passage(state)||Passages.open(state))continue;
            if(!Passages.openable(state)||!c.actions.reachable(pos(door))||!c.actions.useBlock(pos(door))) {invalidate();return;}
            opened.put(pos(door).immutable(),now);status="OPEN door";
            return;
        }
        if(edge.action()==PathAction.OPEN_DOOR) {
            var state=c.player.level().getBlockState(pos(to));
            if(state.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock || state.getBlock() instanceof net.minecraft.world.level.block.LeverBlock) {
                if(!c.actions.useBlock(pos(to))) {invalidate();return;}
                path.remove();
                path.addFirst(PathEdge.move(to,PathAction.MOVE));
                return;
            }
        }
        if(edge.placement()!=null && !world.support(edge.placement())) {
            // Construction is supplied through the same authoritative inventory
            // path as a vanilla /give: no crafting table or recipe step is used.
            if(c.config.placeBlocks && c.config.bridge && c.inventory.buildingCount()<8) {
                Item supply=c.inventory.constructionItem();
                if(!c.inventory.grant(List.of(new ItemStack(supply,64)))) {
                    status="GIVE failed: inventory full; no construction blocks";
                    invalidate();
                    return;
                }
                status="GIVE "+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(supply)+" 64 for navigation";
                return;
            }
            if(!edge.placement().equals(pendingPlacement)){pendingPlacement=edge.placement();placementSource=cell(c.player.blockPosition());placementTicks=0;}
            if(++placementTicks>60){invalidate();pendingPlacement=null;return;}
            c.actions.halt();
            if(edge.action()==PathAction.PILLAR_UP) {
                Vec3 center=new Vec3(edge.placement().x()+0.5,c.player.getY(),edge.placement().z()+0.5);
                if(c.player.position().distanceToSqr(center)>0.04){c.actions.move(center,false);return;}
                c.actions.jump();
                if(c.player.getY()<edge.placement().y()+1) return;
            }
            if(c.actions.placeBlock(pos(edge.placement()),null)==Result.BLOCKED) {
                if(edge.action()==PathAction.BRIDGE_FORWARD&&world.support(placementSource.add(0,-1,0))){
                    var approach=PlacementApproach.bridgeEdge(placementSource,edge.placement(),c.player.getY());
                    if(c.player.position().distanceToSqr(approach)>0.005){c.actions.move(approach,false);c.player.setShiftKeyDown(true);}
                }
                status="PLACE waiting for safe face / clear placement";return;
            }
            c.construction.placed.accept(pos(edge.placement()));
            // Subsequent edges must be searched against the block that was actually placed.
            search=null;extending=false;
            return;
        }
        pendingPlacement=null;placementTicks=0;
        if(!world.clear(to)) {invalidate();return;}
        if(!world.stand(to)) {invalidate();return;}
        Vec3 point=new Vec3(to.x()+0.5,world.feetY(to),to.z()+0.5);
        if(c.player.position().distanceToSqr(point)<0.22) {path.remove();stuck=0;failures=0;if(path.isEmpty()||path.peek().action()!=PathAction.MOVE)c.actions.halt();return;}
        c.actions.move(point,edge.action()==PathAction.MOVE && path.size()>1);
        if(edge.action()==PathAction.JUMP || edge.action()==PathAction.STEP_UP || edge.action()==PathAction.PLACE&&point.y>c.player.getY()+0.4 || c.player.horizontalCollision) c.actions.jump();
        if(edge.action()==PathAction.SWIM && point.y>c.player.getY()) c.actions.jump();
        if(edge.action()==PathAction.CLIMB && point.y>c.player.getY()) c.player.setDeltaMovement(c.player.getDeltaMovement().x,0.2,c.player.getDeltaMovement().z);
        status=edge.action().name();
        if(c.player.position().distanceToSqr(last)<0.015) stuck++; else {stuck=0;last=c.player.position();}
        if(stuck==20) c.actions.jump();
        // Flowing water can carry a companion away from the sampled route.
        // Abandon that edge early, mark it temporarily blocked, and let the
        // bounded A* search choose a dry/alternate route.
        if(c.player.isInWater() && stuck>26) {invalidate();stuck=0;return;}
        // A pure movement stall is commonly another companion occupying a
        // one-wide bridge cell. Do not blacklist the valid block edge for ten
        // seconds; retry it after the short replan delay. Repeated stalls still
        // increase failures and ProtectTask then selects another formation post.
        if(stuck>40) {invalidate(false);stuck=0;}
    }
}
