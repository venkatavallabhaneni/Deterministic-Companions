package dev.companions;

import dev.companions.building.*;
import dev.companions.combat.ThreatPriority;
import dev.companions.inventory.ToolScoring;
import dev.companions.player.CompanionLimit;
import dev.companions.task.*;
import dev.companions.world.BlockSafetyPolicy;
import dev.companions.navigation.path.*;
import dev.companions.navigation.action.PathAction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompanionLogicTest {
    @Test void maximumCountsOnlyActiveSlots() {
        for(int active=0;active<4;active++){int count=active;assertDoesNotThrow(()->CompanionLimit.check(count));}
        assertEquals("Maximum companions reached: 4/4",assertThrows(IllegalStateException.class,()->CompanionLimit.check(4)).getMessage());
        assertDoesNotThrow(()->CompanionLimit.check(3));
    }
    static class Probe implements CompanionTask {
        int starts,ticks,cancels;boolean done;
        public void start(){starts++;}public void tick(){ticks++;}public void cancel(){cancels++;}public boolean isComplete(){return done;}public String getStatus(){return "probe";}
    }
    @Test void taskReplacementCancelsBeforeStartingAndOnlyTicksCurrent() {
        var controller=new TaskController(()->{});var a=new Probe();var b=new Probe();
        controller.set(a);controller.tick();controller.set(b);controller.tick();
        assertEquals(1,a.starts);assertEquals(1,a.ticks);assertEquals(1,a.cancels);assertEquals(1,b.starts);assertEquals(1,b.ticks);
    }
    @Test void completionAndStopHaltAndDoNotTickAgain() {
        int[] halts={0};var controller=new TaskController(()->halts[0]++);var p=new Probe();controller.set(p);
        p.done=true;controller.tick();controller.tick();assertTrue(controller.idle());assertEquals(1,p.ticks);assertEquals(2,halts[0]);
        var next=new Probe();controller.set(next);controller.stop();controller.tick();assertEquals(1,next.cancels);assertEquals(0,next.ticks);
    }
    static class MovingPlayer implements TrackingTarget {
        int livePosition=20,lastDestination=-1,botPosition,approaches,halts;boolean available=true,blocked;
        public boolean available(){return available;}public boolean nearby(){return Math.abs(livePosition-botPosition)<=2;}
        public void approach(){lastDestination=livePosition;approaches++;}public void halt(){halts++;}public boolean blocked(){return blocked;}public String name(){return "owner";}
    }
    @Test void returnTracksMovingPlayerAndCompletesOnlyOnArrival() {
        var player=new MovingPlayer();var task=new ReturnTask(player);task.tick();assertEquals(20,player.lastDestination);assertFalse(task.isComplete());
        player.livePosition=40;task.tick();assertEquals(40,player.lastDestination);assertFalse(task.isComplete());
        player.botPosition=39;task.tick();assertTrue(task.isComplete());assertEquals(1,player.halts);
    }
    @Test void returnReplacesMiningAndCancellationStopsApproach() {
        var mining=new Probe();var player=new MovingPlayer();var tasks=new TaskController(()->{});tasks.set(mining);tasks.set(new ReturnTask(player));tasks.tick();tasks.stop();tasks.tick();
        assertEquals(1,mining.cancels);assertEquals(1,player.approaches);assertEquals(1,player.halts);
    }
    @Test void missingReturnTargetCompletesWithoutStaleMovement() {
        var p=new MovingPlayer();p.available=false;var t=new ReturnTask(p);t.tick();assertTrue(t.isComplete());assertEquals(0,p.approaches);
    }
    @Test void followRemainsActiveAfterArrivalAndResumesWhenPlayerMoves() {
        var p=new MovingPlayer();p.botPosition=20;var t=new FollowTask(p);t.tick();assertFalse(t.isComplete());
        p.livePosition=50;t.tick();assertEquals(50,p.lastDestination);assertFalse(t.isComplete());
    }
    @Test void pathCostsPreferWalkingThenJumpingThenBreakingThenPlacing() {
        assertTrue(PathAction.MOVE.cost()<PathAction.JUMP.cost());assertTrue(PathAction.JUMP.cost()<PathAction.BREAK.cost());assertTrue(PathAction.BREAK.cost()<PathAction.PLACE.cost());
        assertEquals(11,new PathEdge(new Cell(1,0,0),PathAction.BREAK,List.of(),null,3).cost());
    }
    @Test void astarChoosesCheaperDetourAndIsDeterministic() {
        Cell start=new Cell(0,0,0),goal=new Cell(2,0,0);
        java.util.function.Function<Cell,List<PathEdge>> graph=p -> {
            if(p.equals(start))return List.of(PathEdge.move(goal,PathAction.PLACE),PathEdge.move(new Cell(0,0,1),PathAction.MOVE));
            if(p.equals(new Cell(0,0,1)))return List.of(PathEdge.move(new Cell(1,0,1),PathAction.MOVE));
            if(p.equals(new Cell(1,0,1)))return List.of(PathEdge.move(goal,PathAction.JUMP));return List.of();
        };
        var a=new BoundedAStar(start,goal,0,16,100,graph);var b=new BoundedAStar(start,goal,0,16,100,graph);
        while(!a.done())a.step(1);while(!b.done())b.step(2);
        assertTrue(a.reached());assertEquals(3,a.path().size());assertEquals(a.path(),b.path());
    }
    @Test void astarHonorsPerTickAndTotalBudgetOnUnreachableGoal() {
        var a=new BoundedAStar(new Cell(0,0,0),new Cell(100,0,0),0,32,12,p -> List.of(PathEdge.move(p.add(1,0,0),PathAction.MOVE),PathEdge.move(p.add(0,0,1),PathAction.MOVE)));
        a.step(3);assertTrue(a.explored()<=3);while(!a.done())a.step(3);assertTrue(a.explored()<=12);assertFalse(a.reached());assertFalse(a.path().isEmpty());
    }
    @Test void astarNeverCrossesSearchRadius() {
        var a=new BoundedAStar(new Cell(0,0,0),new Cell(20,0,0),0,3,100,p -> List.of(PathEdge.move(p.add(1,0,0),PathAction.MOVE)));
        a.step(100);assertTrue(a.done());assertEquals(new Cell(3,0,0),a.path().getLast().destination());
    }
    @Test void allFiveBuildPlansHaveExpectedGeometryAndNoImplicitRemovals() {
        Cell origin=new Cell(0,64,0);
        assertEquals(40,BuildPlan.generate("wall",origin,0,1,10,4).placements().size());
        assertEquals(20,BuildPlan.generate("floor",origin,0,1,4,5).placements().size());
        var bridge=BuildPlan.generate("bridge",origin,1,0,20,1);assertEquals(new Cell(20,63,0),bridge.placements().getLast());assertTrue(bridge.removals().isEmpty());
        assertEquals(new Cell(0,68,0),BuildPlan.generate("pillar",origin,0,1,5,1).placements().getLast());
        assertEquals(15,BuildPlan.generate("stairs",origin,0,1,5,1).placements().size());
        assertThrows(IllegalArgumentException.class,()->BuildPlan.generate("wall",origin,0,1,65,1));
    }
    @Test void buildPlansDoNotShareMutableState() {
        var placements=new ArrayList<Cell>();placements.add(new Cell(1,2,3));var p=new BuildPlan("x",placements,Set.of(),null);placements.clear();assertEquals(1,p.placements().size());
        assertThrows(UnsupportedOperationException.class,()->p.placements().clear());
    }
    @Test void bridgeSelectsDisposableInventoryAndRejectsValuables() {
        var list=List.of(new BuildingBlockSelector.Candidate(0,"minecraft:diamond_block",64),new BuildingBlockSelector.Candidate(1,"minecraft:chest",64),new BuildingBlockSelector.Candidate(2,"minecraft:dirt",12),new BuildingBlockSelector.Candidate(3,"minecraft:cobblestone",8));
        assertEquals(3,BuildingBlockSelector.select(list));assertEquals(-1,BuildingBlockSelector.select(list.subList(0,2)));
        assertEquals(-1,BuildingBlockSelector.select(List.of(new BuildingBlockSelector.Candidate(0,"minecraft:dirt",0))));
    }
    @Test void toolScoringPrefersCorrectUsableToolsAndEfficiency() {
        assertTrue(ToolScoring.score(true,4,20,100,0)>ToolScoring.score(false,10,100,100,5));
        assertTrue(ToolScoring.score(true,8,50,100,2)>ToolScoring.score(true,4,50,100,0));
        assertEquals(-1,ToolScoring.score(true,8,1,100,2));
    }
    @Test void threatOrderingMatchesProtectionRules() {
        int a=ThreatPriority.score(true,false,false,false,false),b=ThreatPriority.score(false,true,false,false,false),c=ThreatPriority.score(false,false,true,false,false),d=ThreatPriority.score(false,false,false,true,false),e=ThreatPriority.score(false,false,false,false,true);
        assertTrue(a>b&&b>c&&c>d&&d>e);assertEquals(0,ThreatPriority.score(false,false,false,false,false));
    }
    @Test void safetyPreservesContainersAndFunctionalBlocks() {
        for(String id:List.of("chest","barrel","furnace","beacon","spawner","shulker_box"))assertFalse(BlockSafetyPolicy.mayBreak("minecraft:"+id,true,2));
        assertFalse(BlockSafetyPolicy.mayBreak("minecraft:diamond_block",false,5));assertFalse(BlockSafetyPolicy.mayBreak("minecraft:bedrock",false,-1));
        assertTrue(BlockSafetyPolicy.mayBreak("minecraft:iron_ore",false,3));assertTrue(BlockSafetyPolicy.dangerous("minecraft:lava"));
        assertFalse(BlockSafetyPolicy.disposable("minecraft:sand"));assertFalse(BlockSafetyPolicy.disposable("mod:unknown_block"));
    }
}
