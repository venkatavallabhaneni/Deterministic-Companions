package dev.companions;

import dev.companions.navigation.path.Cell;
import dev.companions.schematic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the 08_watch_tower stall: a solid 3x3 cobblestone core whose top
 * centre is still missing can only be finished from on top of the tower.
 * The stand finder must reach it, and every other core block, without a server.
 */
class SchematicStandTest {
    private static final double EYE=1.62;
    private static final List<Direction> SOLID_ORDER=List.of(Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP);

    /** Block grid: listed cells are solid, everything else is air above a flat floor at y=-1. */
    static final class Grid implements StandFinder.View {
        final Set<BlockPos> solid=new HashSet<>();
        Grid(){for(int x=-12;x<=16;x++)for(int z=-12;z<=16;z++)solid.add(new BlockPos(x,-1,z));}
        public boolean loaded(BlockPos p){return true;}
        public boolean hazard(BlockPos p){return false;}
        public boolean clear(BlockPos feet){return !solid.contains(feet)&&!solid.contains(feet.above());}
        public boolean stand(BlockPos feet){return clear(feet)&&solid.contains(feet.below());}
        public boolean replaceable(BlockPos p){return !solid.contains(p);}
        public boolean solid(BlockPos p){return solid.contains(p);}
        public boolean visible(Vec3 from,Vec3 to,BlockPos target){
            Vec3 d=to.subtract(from);int steps=(int)Math.ceil(d.length()/0.01);
            for(int i=1;i<steps;i++){
                BlockPos at=BlockPos.containing(from.add(d.scale(i/(double)steps)));
                if(solid.contains(at))return at.equals(target);
            }
            return true;
        }
    }
    /** Core x1..3 z1..3 from y0 up to and including topLayer; optionally the top centre missing. */
    static Grid tower(int topLayer,boolean centreMissing){
        var g=new Grid();
        for(int y=0;y<=topLayer;y++)for(int x=1;x<=3;x++)for(int z=1;z<=3;z++)g.solid.add(new BlockPos(x,y,z));
        if(centreMissing)g.solid.remove(new BlockPos(2,topLayer,2));
        return g;
    }
    /** What the job charges for standing in a cell: core/platform cells still to be built cost a detour. */
    static double cost(BlockPos p,int activeLayer){
        boolean footprint=p.getX()>=0&&p.getX()<=4&&p.getZ()>=0&&p.getZ()<=4&&p.getY()>=0&&p.getY()<=7;
        if(!footprint)return 0;
        return p.getY()==activeLayer?12:p.getY()>activeLayer?4:0;
    }
    static void assertUsable(Grid g,BlockPos target,BlockPos stand){
        assertNotNull(stand,"no stand found for "+target.toShortString());
        assertNotEquals(target,stand);assertNotEquals(target,stand.above());
        Vec3 eyes=Vec3.atBottomCenterOf(stand).add(0,EYE,0);
        boolean clickable=false;
        for(Vec3 face:StandFinder.faces(g,target,SOLID_ORDER)){
            BlockPos neighbor=BlockPos.containing(face.add(face.subtract(Vec3.atCenterOf(target)).scale(0.01)));
            if(eyes.distanceToSqr(face)<=SchematicPlacer.REACH_SQ&&g.visible(eyes,face,neighbor))clickable=true;
        }
        assertTrue(clickable,"stand "+stand.toShortString()+" cannot click a face of "+target.toShortString());
        // The task only acts with the centre in reach; a stand failing this made builders arrive,
        // do nothing, and wander off to pillar somewhere else.
        assertTrue(eyes.distanceToSqr(Vec3.atCenterOf(target))<=SchematicPlacer.REACH_SQ,"stand "+stand.toShortString()+" leaves the centre of "+target.toShortString()+" out of reach");
    }

    @Test void missingTopCentreOfTheTowerIsReachableFromAbove(){
        var g=tower(5,true);
        var target=new BlockPos(2,5,2);
        var stand=StandFinder.find(g,target,new Vec3(0.5,0,-1.5),EYE,SOLID_ORDER,p->false,p->cost(p,5),Set.of());
        assertUsable(g,target,stand);
        assertTrue(stand.getY()>=6,"the enclosed centre is only clickable from above, got "+stand.toShortString());
    }
    @Test void everyBlockOfTheNextCoreLayerHasAStand(){
        var g=tower(5,false);
        for(int x=1;x<=3;x++)for(int z=1;z<=3;z++){
            var target=new BlockPos(x,6,z);
            assertUsable(g,target,StandFinder.find(g,target,new Vec3(-1.5,0,-1.5),EYE,SOLID_ORDER,p->false,p->cost(p,6),Set.of()));
        }
    }
    /** 03_storage_house: floor y0, cobblestone walls y1-3 around 15 x 11, roof y4 partly built from the walls inward. */
    static Grid house(int roofRingsDone){
        var g=new Grid();
        for(int x=0;x<15;x++)for(int z=0;z<11;z++){
            g.solid.add(new BlockPos(x,0,z));
            boolean wall=x==0||x==14||z==0||z==10;
            if(wall)for(int y=1;y<=3;y++)g.solid.add(new BlockPos(x,y,z));
            int ring=Math.min(Math.min(x,14-x),Math.min(z,10-z));
            if(ring<roofRingsDone)g.solid.add(new BlockPos(x,4,z));
        }
        return g;
    }
    @Test void houseRoofIsPlacedFromTheFloorWithoutPillars(){
        for(int rings=1;rings<=4;rings++){
            var g=house(rings);
            for(int x=rings;x<15-rings;x++)for(int z=rings;z<11-rings;z++){
                int ring=Math.min(Math.min(x,14-x),Math.min(z,10-z));
                if(ring!=rings)continue;
                var target=new BlockPos(x,4,z);
                // Builders approach from wherever their last block was, often across the room.
                for(int fx=1;fx<14;fx+=3)for(int fz=1;fz<10;fz+=4){
                    Vec3 from=new Vec3(fx+0.5,1,fz+0.5);
                    var stand=StandFinder.find(g,target,from,EYE,SOLID_ORDER,p->false,p->cost(p,4),Set.of());
                    assertUsable(g,target,stand);
                    assertTrue(g.stand(stand),"roof cell "+target.toShortString()+" should be reached from the floor, got a pillar at "+stand.toShortString());
                }
            }
        }
    }
    @Test void aFailedStandIsAvoidedAndAnotherIsFound(){
        var g=tower(5,true);var target=new BlockPos(2,5,2);
        var avoid=new HashSet<BlockPos>();
        for(int round=0;round<4;round++){
            var stand=StandFinder.find(g,target,new Vec3(0.5,0,-1.5),EYE,SOLID_ORDER,p->false,p->cost(p,5),avoid);
            assertUsable(g,target,stand);
            assertTrue(avoid.add(stand),"same stand returned twice");
        }
    }
    @Test void otherBuildersReservedSpotsAreNeverUsed(){
        var g=tower(5,true);var target=new BlockPos(2,5,2);
        var first=StandFinder.find(g,target,new Vec3(0.5,0,-1.5),EYE,SOLID_ORDER,p->false,p->cost(p,5),Set.of());
        var second=StandFinder.find(g,target,new Vec3(0.5,0,-1.5),EYE,SOLID_ORDER,p->p.equals(first)||p.equals(first.above())||p.equals(first.below()),p->cost(p,5),Set.of());
        assertUsable(g,target,second);
        assertFalse(second.getX()==first.getX()&&second.getZ()==first.getZ()&&Math.abs(second.getY()-first.getY())<=1,"reserved column reused");
    }
    @Test void aBlockWithNothingToClickHasNoStand(){
        var g=new Grid();
        assertNull(StandFinder.find(g,new BlockPos(0,4,0),Vec3.ZERO,EYE,SOLID_ORDER,p->false,p->0,Set.of()),"caller must build a support first");
    }
    @Test void scaffoldPillarTopCanBeRemovedIncludingFromOnTopOfIt(){
        var g=new Grid();
        for(int y=0;y<=5;y++)g.solid.add(new BlockPos(6,y,6));
        var target=new BlockPos(6,5,6);
        var stand=StandFinder.find(g,target,new Vec3(6.5,6,6.5),EYE,null,p->false,p->0,Set.of());
        assertNotNull(stand);
        Vec3 eyes=Vec3.atBottomCenterOf(stand).add(0,EYE,0);
        assertTrue(eyes.distanceToSqr(Vec3.atCenterOf(target))<=20.25&&g.visible(eyes,Vec3.atCenterOf(target),target));
    }
    @Test void aCellBoxedInOnAllSidesIsStillReachable(){
        var g=new Grid();
        var target=new BlockPos(5,2,5);
        for(var d:Direction.values())g.solid.add(target.relative(d));
        var stand=StandFinder.find(g,target,new Vec3(8.5,0,8.5),EYE,null,false,p->false,p->0,Set.of());
        assertNotNull(stand,"an enclosed cell cannot be seen, so reach alone must do");
        assertTrue(Vec3.atBottomCenterOf(stand).add(0,EYE,0).distanceToSqr(Vec3.atCenterOf(target))<=SchematicPlacer.REACH_SQ);
    }
    @Test void afterAFailedSpotWallBlocksAreWorkedFromOutsideTheBuilding(){
        var g=house(1);
        // A missing block in the top course of the west wall, approached by a builder standing inside.
        var target=new BlockPos(0,3,5);g.solid.remove(target);
        Vec3 inside=new Vec3(3.5,1,5.5);
        var first=StandFinder.find(g,target,inside,EYE,SOLID_ORDER,p->false,p->cost(p,3),Set.of());
        assertUsable(g,target,first);
        java.util.function.ToDoubleFunction<BlockPos> outside=p->cost(p,3)+(p.getX()>=0&&p.getX()<=14&&p.getZ()>=0&&p.getZ()<=10?10:0);
        var second=StandFinder.find(g,target,inside,EYE,SOLID_ORDER,p->false,outside,Set.of(first));
        assertUsable(g,target,second);
        assertTrue(second.getX()<0,"the next try scaffolds up outside the wall, got "+second.toShortString());
    }
    @Test void neverDigsOutABlockFromOnTopOverALongDrop(){
        var g=new Grid();
        // A single block floating 5 above the floor: standing on it and breaking it would mean a 5-block fall.
        var target=new BlockPos(6,5,6);g.solid.add(target);
        var stand=StandFinder.find(g,target,new Vec3(6.5,6,6.5),EYE,null,true,p->false,p->0,Set.of());
        assertNotNull(stand);assertNotEquals(target.above(),stand);
        // With footing just below, digging it out from on top is fine.
        g.solid.add(target.below(2));
        var near=StandFinder.find(g,target,new Vec3(6.5,6,6.5),EYE,null,true,p->false,p->-0.0,Set.of());
        assertNotNull(near);
    }
    @Test void idleBuildersRetryCoolingBlocksInsteadOfStaring(){
        var s=new BuildSchedule(List.of(new BuildSchedule.Entry(new Cell(0,0,0),0),new BuildSchedule.Entry(new Cell(1,0,0),0)));
        UUID a=UUID.randomUUID();
        assertTrue(s.claim(0,a));s.release(0,0,true);
        assertEquals(List.of(1),s.offered(0),"fresh work first");
        assertTrue(s.claim(1,a));s.release(1,0,true);
        assertFalse(s.offered(1).isEmpty(),"with only cooling work left it is offered at once");
        int guard=0;
        while(s.state(0)!=BuildSchedule.State.SKIPPED&&guard++<50){assertTrue(s.claim(0,a));s.release(0,guard,true);}
        assertEquals(BuildSchedule.State.SKIPPED,s.state(0),"retries still count, so an impossible block is skipped");
    }
}
