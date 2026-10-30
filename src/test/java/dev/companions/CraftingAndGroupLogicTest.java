package dev.companions;

import dev.companions.crafting.IngredientAllocator;
import dev.companions.task.*;
import dev.companions.world.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftingAndGroupLogicTest {
    @Test void craftingNeverSpendsAnIngredientTwiceOrMutatesInventoryCounts(){
        int[] inventory={1};assertNull(IngredientAllocator.allocate(2,inventory,(n,s)->true));assertArrayEquals(new int[]{1},inventory);
        assertArrayEquals(new int[]{0,0},IngredientAllocator.allocate(2,new int[]{2},(n,s)->true));
    }
    @Test void overlappingRecipeIngredientsBacktrackDeterministically(){
        assertArrayEquals(new int[]{1,0},IngredientAllocator.allocate(2,new int[]{1,1},(n,s)->n==0||s==0));
        assertArrayEquals(new int[]{0,1},IngredientAllocator.allocate(2,new int[]{1,1},(n,s)->true));
        assertNull(IngredientAllocator.allocate(1,new int[]{0,4},(n,s)->s==0));
    }
    @Test void pathologicalCraftingAssignmentsHaveFiniteWork(){
        int[] calls={0};int[] counts=new int[36];Arrays.fill(counts,1);
        assertNull(IngredientAllocator.allocate(9,counts,(n,s)->{calls[0]++;return n<8;}));assertTrue(calls[0]<=4096);
    }
    @Test void scanIncludesBoth150BlockBoundariesAndRejects151(){
        var origin=new BlockPos(-7,40,15);
        for(int sign:new int[]{-1,1}){
            assertTrue(BlockSearch.inRange(origin,origin.offset(sign*150,0,0)));
            assertTrue(BlockSearch.inRange(origin,origin.offset(0,sign*150,sign*150)));
            assertFalse(BlockSearch.inRange(origin,origin.offset(sign*151,0,0)));
            assertFalse(BlockSearch.inRange(origin,origin.offset(0,0,sign*151)));
        }
    }
    @Test void miningFinishesThenFollowsLivePlayerContinuously(){
        var mine=new CompanionLogicTest.Probe();var player=new CompanionLogicTest.MovingPlayer();int[] reports={0};
        var task=new MineAndFollowTask(mine,new FollowTask(player),()->reports[0]++);
        task.start();task.tick();assertEquals(0,player.approaches);assertEquals(0,reports[0]);
        mine.done=true;task.tick();assertEquals(1,reports[0]);task.tick();assertEquals(20,player.lastDestination);
        player.botPosition=20;task.tick();assertFalse(task.isComplete());
        player.livePosition=55;task.tick();assertEquals(55,player.lastDestination);assertEquals(1,reports[0]);
    }
    @Test void stoppingMiningDoesNotStartFollowOrReportCompletion(){
        var mine=new CompanionLogicTest.Probe();var follow=new CompanionLogicTest.Probe();int[] reports={0};
        var task=new MineAndFollowTask(mine,follow,()->reports[0]++);task.start();task.cancel();task.tick();
        assertEquals(1,mine.cancels);assertEquals(0,follow.starts);assertEquals(0,reports[0]);assertTrue(task.isComplete());
    }
    @Test void replacementAfterMiningCancelsTheActiveFollow(){
        var mine=new CompanionLogicTest.Probe();var follow=new CompanionLogicTest.Probe();mine.done=true;
        var tasks=new TaskController(()->{});tasks.set(new MineAndFollowTask(mine,follow,()->{}));tasks.tick();tasks.set(new CompanionLogicTest.Probe());
        assertEquals(1,follow.starts);assertEquals(1,follow.cancels);assertEquals(0,mine.cancels);
    }
    @Test void botsReserveDifferentTargetsAndReleasedOrExpiredClaimsCanBeReused(){
        var claims=new ResourceClaims();UUID bob=new UUID(0,1),alice=new UUID(0,2);
        assertTrue(claims.claim(bob,"overworld",10,0));assertFalse(claims.claim(alice,"overworld",10,1));
        assertTrue(claims.claim(alice,"overworld",11,1));assertTrue(claims.claim(bob,"overworld",10,150));
        assertFalse(claims.available(alice,"overworld",10,250));assertTrue(claims.available(alice,"overworld",10,350));
        claims.claim(bob,"overworld",10,400);claims.release(bob);assertTrue(claims.claim(alice,"overworld",10,401));
    }
    @Test void miningClaimsAreDimensionScopedAndEachBotOwnsAtMostOne(){
        var claims=new ResourceClaims();UUID bob=new UUID(0,1),alice=new UUID(0,2);
        assertTrue(claims.claim(bob,"overworld",10,0));assertTrue(claims.claim(alice,"nether",10,0));
        assertTrue(claims.claim(bob,"overworld",11,1));assertTrue(claims.available(alice,"overworld",10,1));
    }
    @Test void ancientDebrisIsAnExplicitHarvestTargetAndNeverADisposableObstacle(){
        assertTrue(BlockSafetyPolicy.mayBreak("minecraft:ancient_debris",false,30,true));
        assertFalse(BlockSafetyPolicy.mayBreak("minecraft:ancient_debris",false,30));assertFalse(BlockSafetyPolicy.disposable("minecraft:ancient_debris"));
    }
}
