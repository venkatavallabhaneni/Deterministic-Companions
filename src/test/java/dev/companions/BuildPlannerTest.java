package dev.companions;

import dev.companions.schematic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The pre-build planner on block grids: stands, direct placements and shared scaffold towers. */
class BuildPlannerTest {
    static final List<Direction> SOLID=List.of(Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP);
    static VoxelModel site(){
        var m=new VoxelModel(new BlockPos(-10,-6,-10),new BlockPos(30,24,30));
        for(int x=-10;x<=30;x++)for(int z=-10;z<=30;z++)m.setSolid(new BlockPos(x,-1,z),true);
        return m;
    }
    /** Blocks in the order the build places them: layer by layer, each layer ring by ring from its supported cells. */
    static List<BuildPlanner.Block> ordered(List<BlockPos> cells){
        var entries=new ArrayList<BuildSchedule.Entry>();
        for(var p:cells)entries.add(new BuildSchedule.Entry(new dev.companions.navigation.path.Cell(p.getX(),p.getY(),p.getZ()),0));
        var s=new BuildSchedule(entries);
        var out=new ArrayList<BuildPlanner.Block>();
        for(int i=0;i<cells.size();i++)out.add(new BuildPlanner.Block(cells.get(i),true,SOLID,0,s.ring(i)));
        return out;
    }
    @Test void rayCastingStopsAtTheFirstSolidBlock(){
        var m=site();
        m.setSolid(new BlockPos(3,1,0),true);
        assertTrue(m.visible(new Vec3(0.5,1.5,0.5),new Vec3(2.5,1.5,0.5),new BlockPos(9,9,9)),"open air");
        assertFalse(m.visible(new Vec3(0.5,1.5,0.5),new Vec3(5.5,1.5,0.5),new BlockPos(5,1,0)),"a wall in between");
        assertTrue(m.visible(new Vec3(0.5,1.5,0.5),new Vec3(3.0,1.5,0.5),new BlockPos(3,1,0)),"the face of the target itself");
        assertTrue(m.visible(new Vec3(0.5,2.6,0.5),new Vec3(4.5,0.0,0.5),new BlockPos(4,-1,0)),"diagonal down onto the ground");
    }
    @Test void aHouseIsPlannedWithoutScaffold(){
        var cells=new ArrayList<BlockPos>();
        for(int x=0;x<9;x++)for(int z=0;z<7;z++){
            cells.add(new BlockPos(x,0,z));
            if(x==0||x==8||z==0||z==6)for(int y=1;y<=3;y++)if(!(x==4&&z==0&&y<=2))cells.add(new BlockPos(x,y,z));
            cells.add(new BlockPos(x,4,z));
        }
        var r=BuildPlanner.plan(site(),ordered(cells),new BlockPos(0,0,0),new BlockPos(8,4,6),1.62);
        assertEquals(cells.size(),r.steps().size());
        assertEquals(0,r.unreachable());
        assertTrue(r.scaffold().isEmpty(),"a one-storey house is built from the ground and its own floor: "+r.summary());
        for(var s:r.steps().values())assertNotNull(s.stand());
    }
    @Test void aTallTowerSharesAFewScaffoldTowersBuiltOutside(){
        var cells=new ArrayList<BlockPos>();
        for(int y=0;y<12;y++)for(int x=0;x<3;x++)for(int z=0;z<3;z++)cells.add(new BlockPos(x,y,z));
        var r=BuildPlanner.plan(site(),ordered(cells),new BlockPos(0,0,0),new BlockPos(2,11,2),1.62);
        assertEquals(0,r.unreachable(),r.summary());
        assertFalse(r.scaffold().isEmpty(),"a 12-high tower needs scaffolding");
        assertTrue(r.towers().size()<=4,"towers are reused, not one per block: "+r.summary());
        for(var p:r.scaffold())assertFalse(p.getX()>=0&&p.getX()<=2&&p.getZ()>=0&&p.getZ()<=2,"scaffold goes outside the tower: "+p);
        int fromScaffold=r.counts().getOrDefault(BuildPlanner.How.SCAFFOLD,0);
        assertTrue(fromScaffold>r.towers().size()*3,"each tower serves many blocks: "+r.summary());
    }
    @Test void planningIsDeterministic(){
        var cells=new ArrayList<BlockPos>();
        for(int y=0;y<8;y++)for(int x=0;x<4;x++)cells.add(new BlockPos(x,y,0));
        var a=BuildPlanner.plan(site(),ordered(cells),new BlockPos(0,0,0),new BlockPos(3,7,0),1.62);
        var b=BuildPlanner.plan(site(),ordered(cells),new BlockPos(0,0,0),new BlockPos(3,7,0),1.62);
        assertEquals(a.steps(),b.steps());assertEquals(a.scaffold(),b.scaffold());
    }
    @Test void aBlockWithNothingToClickIsPlannedAsDirect(){
        var cells=List.of(new BlockPos(5,3,5));
        var r=BuildPlanner.plan(site(),ordered(cells),new BlockPos(5,3,5),new BlockPos(5,3,5),1.62);
        var step=r.steps().get(0);
        assertTrue(step.direct(),"no neighbour to click: aim at the cell");
    }
}
