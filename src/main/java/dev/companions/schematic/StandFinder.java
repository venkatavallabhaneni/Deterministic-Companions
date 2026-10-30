package dev.companions.schematic;

import dev.companions.navigation.TraversalGeometry;
import dev.companions.player.CompanionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.*;
import static dev.companions.navigation.WorldNeighbors.*;

/**
 * Chooses where a builder stands to reach a block. Standable cells are free;
 * an air cell above a column of replaceable cells is accepted too, and
 * navigation reaches it by pillaring up on temporary scaffold blocks.
 *
 * A placement stand must see a real clickable face (the same hit points
 * {@link SchematicPlacer} uses), not just the target's centre: a block
 * enclosed on four sides can only be clicked from above. Cells that are still
 * to be built are a cost, never forbidden, so standing on top of an unfinished
 * structure (the only way to finish its centre) stays possible.
 */
public final class StandFinder {
    public static final int MAX_PILLAR=10;
    private static final double REACH=4.3,REACH_SQ=REACH*REACH,CENTRE_REACH_SQ=4.3*4.3;
    private StandFinder(){}

    /** The world as seen by the finder; implemented over the live level and by test grids. */
    public interface View {
        boolean loaded(BlockPos p);
        boolean hazard(BlockPos p);
        /** Body fits at feet and feet+1. */
        boolean clear(BlockPos feet);
        /** The companion can stay put here without sliding (solid footing, not just a ladder). */
        boolean stand(BlockPos feet);
        boolean replaceable(BlockPos p);
        /** Has a collision shape, so it can be clicked to place against. */
        boolean solid(BlockPos p);
        boolean visible(Vec3 from,Vec3 to,BlockPos target);
    }
    public static View view(CompanionController c){
        var world=c.navigation.world;var level=c.player.level();
        return new View(){
            public boolean loaded(BlockPos p){return world.loaded(cell(p));}
            public boolean hazard(BlockPos p){return world.hazard(cell(p));}
            // Never park in a doorway: the door may be shut on the companion or block everyone else.
            public boolean clear(BlockPos p){return world.clear(cell(p))&&!dev.companions.navigation.Passages.passage(level.getBlockState(p))&&!dev.companions.navigation.Passages.passage(level.getBlockState(p.above()));}
            // A companion holding still on a ladder or vine slowly slides down.
            public boolean stand(BlockPos p){var x=cell(p);return world.stand(x)&&!(world.climb(x)&&!world.support(x.add(0,-1,0)));}
            public boolean replaceable(BlockPos p){return world.replaceable(cell(p));}
            public boolean solid(BlockPos p){return level.hasChunkAt(p)&&!level.getBlockState(p).getCollisionShape(level,p).isEmpty();}
            public boolean visible(Vec3 from,Vec3 to,BlockPos target){return TraversalGeometry.visible(level,c.player,from,to,target);}
        };
    }
    /** Points a placement may click: the face of each solid neighbour that touches the target. */
    public static List<Vec3> faces(View v,BlockPos target,List<Direction> supportOrder){
        var out=new ArrayList<Vec3>(6);
        for(Direction toward:supportOrder){
            BlockPos neighbor=target.relative(toward);
            if(!v.solid(neighbor))continue;
            out.add(Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(toward.getOpposite().getUnitVec3i()).scale(0.5)));
        }
        return out;
    }

    /**
     * @param supportOrder null when removing the block (aim at its centre), else the placement support order
     * @param blocked hard exclusions for body and pillar cells (other builders' reserved spots)
     * @param cost extra cost for body and pillar cells (e.g. blocks still to be built)
     * @param avoid stands that already failed for this block
     */
    public static BlockPos find(View v,BlockPos target,Vec3 from,double eyeHeight,List<Direction> supportOrder,
                                Predicate<BlockPos> blocked,ToDoubleFunction<BlockPos> cost,Set<BlockPos> avoid){
        return find(v,target,from,eyeHeight,supportOrder,supportOrder==null,blocked,cost,avoid);
    }
    /**
     * @param onTop allow standing directly on the target (digging it out from above). Only offered when
     *              there is footing at most 3 blocks under the target, so breaking it never means a long fall.
     */
    public static BlockPos find(View v,BlockPos target,Vec3 from,double eyeHeight,List<Direction> supportOrder,boolean onTop,
                                Predicate<BlockPos> blocked,ToDoubleFunction<BlockPos> cost,Set<BlockPos> avoid){
        boolean breaking=supportOrder==null;
        boolean landing=false;
        for(int k=1;k<=3&&!landing;k++)landing=v.solid(target.below(k));
        // A cell boxed in on all six sides cannot be seen from anywhere; being in reach has to do.
        boolean enclosed=breaking;
        for(Direction d:Direction.values())if(enclosed&&!v.solid(target.relative(d)))enclosed=false;
        List<Vec3> aims=breaking?List.of(Vec3.atCenterOf(target)):faces(v,target,supportOrder);
        if(aims.isEmpty())return null;
        BlockPos best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int dy=-6;dy<=2;dy++)for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            BlockPos feet=target.offset(dx,dy,dz);
            if(feet.equals(target)||feet.above().equals(target)||avoid.contains(feet))continue;
            if(dx==0&&dz==0&&dy==1&&(!onTop||!landing))continue;
            // Never plan a pillar or stand straight through the target column from above.
            if(dx==0&&dz==0&&dy>1&&!onTop)continue;
            Vec3 eyes=Vec3.atBottomCenterOf(feet).add(0,eyeHeight,0);
            // The builder acts only with the block centre in reach (SchematicPlacer.inReach and
            // WorldActionController.reachable), so a stand must satisfy that as well as see a face.
            double centre=eyes.distanceToSqr(Vec3.atCenterOf(target));
            if(centre>CENTRE_REACH_SQ)continue;
            if(blocked.test(feet)||blocked.test(feet.above()))continue;
            if(!v.loaded(feet)||v.hazard(feet)||!v.clear(feet))continue;
            double score=cost.applyAsDouble(feet)+cost.applyAsDouble(feet.above());
            if(!v.stand(feet)){
                if(dx==0&&dz==0)continue;
                double pillar=pillarCost(v,feet,blocked,cost);
                if(pillar<0)continue;
                score+=pillar;
            }else if(dx==0&&dz==0&&dy==1)score+=2;
            score+=Vec3.atBottomCenterOf(feet).distanceToSqr(from)*0.05+Math.sqrt(centre);
            if(score>=bestScore)continue;
            if(!enclosed&&!sees(v,eyes,target,aims,breaking))continue;
            bestScore=score;best=feet;
        }
        return best;
    }
    private static boolean sees(View v,Vec3 eyes,BlockPos target,List<Vec3> aims,boolean breaking){
        for(Vec3 aim:aims){
            if(eyes.distanceToSqr(aim)>REACH_SQ)continue;
            BlockPos hit=breaking?target:BlockPos.containing(aim.add(Vec3.atCenterOf(target).subtract(aim).scale(-0.01)));
            if(v.visible(eyes,aim,hit))return true;
        }
        return false;
    }
    /** Cost of pillaring from solid footing up to feet, or -1 when impossible. */
    static double pillarCost(View v,BlockPos feet,Predicate<BlockPos> blocked,ToDoubleFunction<BlockPos> cost){
        double extra=0;
        for(int n=1;n<=MAX_PILLAR;n++){
            BlockPos below=feet.below(n);
            if(!v.loaded(below)||v.hazard(below))return -1;
            if(v.stand(below))return n*4+extra;
            if(!v.replaceable(below)||!v.clear(below)||blocked.test(below))return -1;
            extra+=cost.applyAsDouble(below);
        }
        return -1;
    }
    /** Nearest standable cell outside the footprint, to wait without blocking other builders. */
    public static BlockPos park(View v,SchematicPlan plan,Vec3 from,Predicate<BlockPos> occupied){
        BlockPos best=null;double bestScore=Double.POSITIVE_INFINITY;
        int y0=plan.min().getY();
        for(int x=plan.min().getX()-3;x<=plan.max().getX()+3;x++)for(int z=plan.min().getZ()-3;z<=plan.max().getZ()+3;z++){
            boolean ring=x<plan.min().getX()-1||x>plan.max().getX()+1||z<plan.min().getZ()-1||z>plan.max().getZ()+1;
            if(!ring)continue;
            for(int y=y0+3;y>=y0-4;y--){
                BlockPos feet=new BlockPos(x,y,z);
                if(occupied.test(feet)||!v.loaded(feet)||!v.stand(feet))continue;
                double score=Vec3.atBottomCenterOf(feet).distanceToSqr(from)+Math.abs(y-y0)*4;
                if(score<bestScore){bestScore=score;best=feet;}
                break;
            }
        }
        return best;
    }
}
