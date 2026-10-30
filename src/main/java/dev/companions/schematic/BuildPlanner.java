package dev.companions.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Plans a whole build before anyone moves. Pure: runs on a background thread
 * over a {@link VoxelModel} snapshot of the cleared site.
 *
 * Blocks are simulated in exactly the order the build runs (phase, layer,
 * ring), walking from block to block like a builder would. For each block the
 * planner picks where to stand (the same {@link StandFinder} the builders use
 * live), whether the block can be clicked into place or has to be set
 * directly, and — when no footing is in reach — a scaffold tower. Towers are
 * added to the model as solid, so later blocks reuse them; spots outside the
 * footprint are preferred so towers stay out of the building's way.
 */
public final class BuildPlanner {
    /** One block to plan: where, whether it is solid once placed, and the faces it is clicked against. */
    public record Block(BlockPos pos,boolean solid,List<Direction> supportOrder,int stage,int ring) {}
    public enum How {GROUND,STRUCTURE,SCAFFOLD,DIRECT}
    public record Step(BlockPos stand,How how,boolean direct) {}
    public record Result(Map<Integer,Step> steps,List<BlockPos> scaffold,List<BlockPos> towers,EnumMap<How,Integer> counts,int unreachable) {
        public String summary(){
            return counts.getOrDefault(How.GROUND,0)+" from the ground, "+counts.getOrDefault(How.STRUCTURE,0)+" from the structure, "
                +counts.getOrDefault(How.SCAFFOLD,0)+" from "+towers.size()+" scaffold tower(s) ("+scaffold.size()+" scaffold blocks)"
                +(counts.getOrDefault(How.DIRECT,0)>0?", "+counts.get(How.DIRECT)+" set directly":"")
                +(unreachable>0?", "+unreachable+" with no planned spot":"");
        }
    }
    private BuildPlanner(){}

    /** @param blocks in build order (index = schedule index); @param footprint min/max of the schematic */
    public static Result plan(VoxelModel model,List<Block> blocks,BlockPos footMin,BlockPos footMax,double eyeHeight){
        var steps=new HashMap<Integer,Step>();
        var scaffold=new ArrayList<BlockPos>();var towers=new ArrayList<BlockPos>();
        var scaffoldSet=new HashSet<BlockPos>();
        var counts=new EnumMap<How,Integer>(How.class);
        var pending=new HashSet<BlockPos>();for(var b:blocks)pending.add(b.pos());
        int unreachable=0;
        // Visit order: phase, layer, ring; inside a ring, nearest next block (a builder walking along).
        var groups=new TreeMap<Long,List<Integer>>();
        for(int i=0;i<blocks.size();i++){
            var b=blocks.get(i);
            long key=((long)b.stage()<<40)|((long)(b.pos().getY()+2048)<<20)|b.ring();
            groups.computeIfAbsent(key,k->new ArrayList<>()).add(i);
        }
        Vec3 at=Vec3.atBottomCenterOf(new BlockPos((footMin.getX()+footMax.getX())/2,footMin.getY(),footMin.getZ()-2));
        for(var group:groups.values()){
            var left=new ArrayList<>(group);
            while(!left.isEmpty()){
                int bestK=0;double bestD=Double.POSITIVE_INFINITY;
                for(int k=0;k<left.size();k++){double d=Vec3.atCenterOf(blocks.get(left.get(k)).pos()).distanceToSqr(at);if(d<bestD){bestD=d;bestK=k;}}
                int i=left.remove(bestK);
                var b=blocks.get(i);
                pending.remove(b.pos());
                // Standing in or pillaring through cells still to be built costs a detour; towers inside
                // the footprint cost a little more so they go up outside the walls when possible.
                java.util.function.ToDoubleFunction<BlockPos> cost=p->(pending.contains(p)?4:0)
                    +(!model.solid(p.below())&&!scaffoldSet.contains(p.below())&&inside(p,footMin,footMax)?3:0);
                BlockPos stand=StandFinder.find(model,b.pos(),at,eyeHeight,b.supportOrder(),false,p->false,cost,Set.of());
                boolean direct=false;
                if(stand==null){stand=StandFinder.find(model,b.pos(),at,eyeHeight,null,false,p->false,cost,Set.of());direct=true;}
                How how;
                if(stand==null){unreachable++;how=How.DIRECT;direct=true;}
                else if(model.stand(stand))how=stand.getY()<=footMin.getY()?How.GROUND:scaffoldSet.contains(stand.below())?How.SCAFFOLD:How.STRUCTURE;
                else{
                    // A tower: every replaceable cell from footing up to the stand becomes scaffold.
                    how=How.SCAFFOLD;
                    var column=new ArrayList<BlockPos>();
                    for(BlockPos p=stand.below();model.replaceable(p)&&column.size()<StandFinder.MAX_PILLAR;p=p.below())column.add(p);
                    if(column.isEmpty()||!scaffoldSet.contains(column.getLast().below()))towers.add(column.isEmpty()?stand:column.getLast());
                    for(var p:column){model.setSolid(p,true);scaffoldSet.add(p);scaffold.add(p);}
                }
                if(direct)how=How.DIRECT;
                counts.merge(how,1,Integer::sum);
                steps.put(i,new Step(stand,how,direct));
                if(b.solid())model.setSolid(b.pos(),true);
                if(stand!=null)at=Vec3.atBottomCenterOf(stand);
            }
        }
        return new Result(steps,scaffold,towers,counts,unreachable);
    }
    private static boolean inside(BlockPos p,BlockPos min,BlockPos max){
        return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();
    }
}
