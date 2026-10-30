package dev.companions.navigation;

import dev.companions.player.CompanionController;
import dev.companions.navigation.path.*;
import dev.companions.navigation.action.PathAction;
import dev.companions.world.BlockSafetyPolicy;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** World reads are restricted to the server thread and already loaded chunks. */
public final class WorldNeighbors implements ConstructionMoves.Terrain {
    private final CompanionController c;
    private final Map<net.minecraft.world.level.block.state.BlockState,Double> breakCosts=new HashMap<>();
    private boolean scanning;
    private final Map<Cell,Boolean> clearScan=new HashMap<>(),supportScan=new HashMap<>();
    private final Map<Cell,Double> heightScan=new HashMap<>();
    // Cache only inside one server-thread search slice. No stale collision
    // results survive into movement, placement, or the next world tick.
    public void beginScan(){clearScan.clear();supportScan.clear();heightScan.clear();scanning=true;}
    public void endScan(){scanning=false;clearScan.clear();supportScan.clear();heightScan.clear();}
    public WorldNeighbors(CompanionController c) { this.c=c; }
    public void newSearch() {breakCosts.clear();}
    public static Cell cell(BlockPos p) { return new Cell(p.getX(),p.getY(),p.getZ()); }
    public static BlockPos pos(Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public boolean loaded(Cell p) {return c.player.level().hasChunkAt(pos(p)) && !c.player.level().isOutsideBuildHeight(pos(p)) && c.player.level().getWorldBorder().isWithinBounds(pos(p));}
    public boolean hazard(Cell p) {return !loaded(p) || BlockSafetyPolicy.dangerous(BuiltInRegistries.BLOCK.getKey(c.player.level().getBlockState(pos(p)).getBlock()).toString());}
    public boolean clear(Cell p) {
        return scanning?clearScan.computeIfAbsent(p,this::readClear):readClear(p);
    }
    private boolean readClear(Cell p) {
        if(!loaded(p)||!loaded(p.add(0,1,0))) return false;
        double y=feetY(p);
        return TraversalGeometry.clearBody(c.player.level(),c.player,pos(p),y);
    }
    public double feetY(Cell p) {
        return scanning?heightScan.computeIfAbsent(p,this::readFeetY):readFeetY(p);
    }
    private double readFeetY(Cell p) {
        if(!loaded(p))return p.y();
        var shape=c.player.level().getBlockState(pos(p)).getCollisionShape(c.player.level(),pos(p));
        if(!shape.isEmpty()) {double top=shape.max(Direction.Axis.Y);if(top>0 && top<=0.6)return p.y()+top;}
        return p.y();
    }
    public boolean support(Cell p) {
        return scanning?supportScan.computeIfAbsent(p,this::readSupport):readSupport(p);
    }
    private boolean readSupport(Cell p) {
        if(!loaded(p)||hazard(p))return false;
        var shape=c.player.level().getBlockState(pos(p)).getCollisionShape(c.player.level(),pos(p));
        return !shape.isEmpty() && Math.abs(shape.max(Direction.Axis.Y)-1)<0.001;
    }
    public boolean water(Cell p) {return loaded(p) && c.player.level().getFluidState(pos(p)).is(FluidTags.WATER);}
    public boolean climb(Cell p) {return loaded(p) && c.player.level().getBlockState(pos(p)).is(BlockTags.CLIMBABLE);}
    public boolean stand(Cell p) {return clear(p) && (feetY(p)>p.y() || support(p.add(0,-1,0)) || water(p) || climb(p));}
    public boolean safe(Cell p){return !hazard(p);}
    public boolean replaceable(Cell p){return loaded(p)&&c.player.level().getBlockState(pos(p)).canBeReplaced();}
    public List<PathEdge> edges(Cell from) {return edges(from,null);}
    /** @param via the edge the route used to reach from, or null; a pillar step below is treated as solid footing */
    public List<PathEdge> edges(Cell from,PathEdge via) {
        boolean pillared=via!=null&&via.action()==PathAction.PILLAR_UP&&from.add(0,-1,0).equals(via.placement());
        List<PathEdge> out=new ArrayList<>(12);
        int materials=c.config.bridge&&c.config.placeBlocks?c.inventory.buildingCount():0;
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            Cell to=from.add(direction.getStepX(),0,direction.getStepZ());
            if(!loaded(to) || hazard(to) || hazard(to.add(0,1,0))) continue;
            var destinationState=c.player.level().getBlockState(pos(to));
            if(Passages.passage(destinationState)){
                // Closed doors/gates look walkable to the body test; route through them only by opening.
                if(!Passages.openable(destinationState)&&!Passages.open(destinationState))continue;
                if(!Passages.open(destinationState)){
                    if(support(to.add(0,-1,0))&&!hazard(to.add(0,1,0)))out.add(PathEdge.move(to,PathAction.OPEN_DOOR));
                    continue;
                }
            }
            // A button/lever is a deliberate interaction edge. It is emitted
            // before the normal walk edge so the fake player presses it while
            // passing, using an empty hand through WorldActionController.
            boolean interactable=destinationState.getBlock() instanceof ButtonBlock || destinationState.getBlock() instanceof LeverBlock;
            if(interactable) out.add(PathEdge.move(to,PathAction.OPEN_DOOR));
            out.addAll(ConstructionMoves.horizontal(from,to,materials,this));
            if(stand(to)) {if(!interactable)out.add(PathEdge.move(to,water(to)?PathAction.SWIM:feetY(to)>feetY(from)?PathAction.STEP_UP:PathAction.MOVE));continue;}
            if(clear(to)) {
                for(int fall=1;fall<=c.construction.maxFall;fall++) {
                    Cell down=to.add(0,-fall,0);
                    if(hazard(down)) break;
                    if(stand(down)) {out.add(PathEdge.move(down,PathAction.SAFE_FALL));break;}
                    if(!clear(down)) break;
                }
            }
            Cell up=to.add(0,1,0);
            if(stand(up) && clear(from.add(0,1,0))) out.add(PathEdge.move(up,PathAction.JUMP));
            var state=destinationState;
            if(c.config.breakBlocks && support(to.add(0,-1,0))) {
                List<Cell> breaks=new ArrayList<>(); double cost=0; boolean allowed=true;
                for(Cell b:List.of(to,to.add(0,1,0))) {
                    var s=c.player.level().getBlockState(pos(b));
                    if(!TraversalGeometry.blocksMovement(c.player.level(),pos(b))) continue;
                    if(!c.actions.mayBreak(pos(b))) {allowed=false;break;}
                    breaks.add(b); cost+=breakCosts.computeIfAbsent(s,key -> c.inventory.estimateBreakTicks(key,pos(b)))*0.2+c.construction.breakCost.applyAsDouble(b);
                }
                if(allowed&&!breaks.isEmpty()) out.add(new PathEdge(to,PathAction.BREAK,breaks,null,cost));
            }
        }
        for(int dy:new int[]{1,-1}) {
            Cell to=from.add(0,dy,0);
            if(clear(to) && (water(from)&&water(to) || climb(from)&&climb(to))) out.add(PathEdge.move(to,water(from)?PathAction.SWIM:PathAction.CLIMB));
        }
        out.addAll(ConstructionMoves.pillar(from,materials,this,pillared));
        Cell below=from.add(0,-1,0);
        if(c.config.breakBlocks && support(below.add(0,-1,0)) && c.actions.mayBreak(pos(below)) && !hazard(below))
            out.add(new PathEdge(below,PathAction.DIG_DOWN_SAFE,List.of(below),null,4+c.construction.breakCost.applyAsDouble(below)));
        return out;
    }
}
