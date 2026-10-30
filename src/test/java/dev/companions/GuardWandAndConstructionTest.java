package dev.companions;

import dev.companions.selection.*;
import dev.companions.combat.GuardArea;
import dev.companions.navigation.ConstructionMoves;
import dev.companions.navigation.path.*;
import dev.companions.navigation.action.PathAction;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuardWandAndConstructionTest {
    @BeforeAll static void bootstrap(){VanillaCraftingDataTest.bootstrap();}
    @Test void commaListsNormalizeNamesAndRejectAmbiguousTargets(){
        assertEquals(List.of("bob","tracy"),GuardSelection.parseNames("Bob, tracy"));assertEquals(List.of("all"),GuardSelection.parseNames("ALL"));
        for(String bad:List.of("","bob,","bob,bob","bob,all","a,b,c,d,e","bob tracy","29 1 0 19 10 0 0"))assertThrows(IllegalArgumentException.class,()->GuardSelection.parseNames(bad));
    }
    @Test void twoClicksPreserveXYZAndReverseCorners(){
        var selection=new GuardSelection(List.of("bob","tracy"),"overworld");
        selection.select(true,new GuardSelection.Corner(29,1,0),"overworld");assertFalse(selection.complete());
        selection.select(false,new GuardSelection.Corner(19,10,5),"overworld");assertEquals(new GuardArea(19,1,0,29,10,5),selection.preview());
    }
    @Test void invalidDimensionSizeOrMissingFirstCornerCannotCommit(){
        var selection=new GuardSelection(List.of("all"),"overworld");
        assertThrows(IllegalArgumentException.class,()->selection.select(false,new GuardSelection.Corner(0,0,0),"overworld"));
        selection.select(true,new GuardSelection.Corner(0,0,0),"overworld");
        assertThrows(IllegalArgumentException.class,()->selection.select(false,new GuardSelection.Corner(128,0,0),"overworld"));
        assertThrows(IllegalArgumentException.class,()->selection.select(false,new GuardSelection.Corner(1,0,0),"nether"));assertFalse(selection.complete());
    }
    @Test void separateOperatorsAndRepeatedClicksDoNotShareOrRestartAssignments(){
        var a=new GuardSelection(List.of("bob"),"overworld");var b=new GuardSelection(List.of("tracy"),"overworld");
        a.select(true,new GuardSelection.Corner(0,0,0),"overworld");a.select(false,new GuardSelection.Corner(1,2,3),"overworld");a.markApplied();
        a.select(false,new GuardSelection.Corner(1,2,3),"overworld");assertTrue(a.applied());assertNull(b.preview());
        a.select(true,new GuardSelection.Corner(4,5,6),"overworld");assertFalse(a.applied());assertFalse(a.complete());
    }
    @Test void outlineContainsEightXYZCornersAndHasBoundedParticleCount(){
        var area=new GuardArea(-64,-64,-64,63,63,63);var points=SelectionOutline.points(area);assertTrue(points.size()<=204);
        for(int x:new int[]{-64,64})for(int y:new int[]{-64,64})for(int z:new int[]{-64,64})assertTrue(points.contains(new SelectionOutline.Point(x,y,z)));
        for(var p:points){int boundary=(p.x()==-64||p.x()==64?1:0)+(p.y()==-64||p.y()==64?1:0)+(p.z()==-64||p.z()==64?1:0);assertTrue(boundary>=2);}
        assertEquals(8,SelectionOutline.points(new GuardArea(0,0,0,0,0,0)).size());
    }
    @Test void ordinarySticksAndRenamedItemsAreNotSelectionWands(){
        var stick=new ItemStack(Items.STICK);assertFalse(GuardWand.isWand(stick));stick.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Guard Area Wand"));assertFalse(GuardWand.isWand(stick));
        var tag=new CompoundTag();tag.putBoolean("companions_guard_wand",true);stick.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));assertTrue(GuardWand.isWand(stick));
    }
    private static class Terrain implements ConstructionMoves.Terrain {
        final Set<Cell> solids=new HashSet<>(),hazards=new HashSet<>();
        public boolean support(Cell p){return solids.contains(p);}
        public boolean clear(Cell p){return !solids.contains(p)&&!solids.contains(p.add(0,1,0))&&!hazards.contains(p);}
        public boolean replaceable(Cell p){return !solids.contains(p);}
        public boolean safe(Cell p){return !hazards.contains(p);}
    }
    @Test void bridgeIsOfferedEvenWhenSafeDropExistsAndRequiresRealMaterial(){
        var t=new Terrain();var from=new Cell(0,1,0);var to=new Cell(1,1,0);t.solids.add(from.add(0,-1,0));t.solids.add(to.add(0,-3,0));
        var edges=ConstructionMoves.horizontal(from,to,1,t);assertTrue(edges.stream().anyMatch(e->e.action()==PathAction.BRIDGE_FORWARD&&e.placement().equals(to.add(0,-1,0))));
        assertTrue(ConstructionMoves.horizontal(from,to,0,t).isEmpty());t.hazards.add(to.add(0,-1,0));assertTrue(ConstructionMoves.horizontal(from,to,1,t).isEmpty());
    }
    @Test void placedStepAndPillarRequireSupportClearanceAndInventory(){
        var t=new Terrain();var from=new Cell(0,1,0);var to=new Cell(1,1,0);t.solids.add(from.add(0,-1,0));t.solids.add(to.add(0,-1,0));
        assertTrue(ConstructionMoves.horizontal(from,to,1,t).stream().anyMatch(e->e.action()==PathAction.PLACE&&e.destination().equals(to.add(0,1,0))));
        assertEquals(1,ConstructionMoves.pillar(from,1,t).size());assertTrue(ConstructionMoves.pillar(from,0,t).isEmpty());
        t.solids.add(from.add(0,2,0));assertTrue(ConstructionMoves.pillar(from,1,t).isEmpty());assertTrue(ConstructionMoves.horizontal(from,to,1,t).isEmpty());
    }
    @Test void incrementalPillarSearchMakesProgressWithEachConsumedPlacement(){
        var t=new Terrain();Cell current=new Cell(0,1,0),goal=new Cell(0,4,0);t.solids.add(current.add(0,-1,0));int blocks=3;
        while(!current.equals(goal)){
            final int available=blocks;var search=new BoundedAStar(current,goal,0,8,32,p->ConstructionMoves.pillar(p,available,t));search.step(32);
            assertFalse(search.path().isEmpty());var edge=search.path().getFirst();assertTrue(t.replaceable(edge.placement()));t.solids.add(edge.placement());blocks--;current=edge.destination();
        }
        assertEquals(0,blocks);assertTrue(ConstructionMoves.pillar(current,blocks,t).isEmpty());
    }
}
