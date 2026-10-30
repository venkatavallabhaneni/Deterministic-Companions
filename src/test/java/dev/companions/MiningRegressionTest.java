package dev.companions;

import dev.companions.world.BlockSafetyPolicy;
import dev.companions.task.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MiningRegressionTest {
    @ParameterizedTest @ValueSource(strings={"anvil","chipped_anvil","damaged_anvil"})
    void explicitAnvilMiningAllowedButNavigationAndBuildingStayProtected(String name) {
        String id="minecraft:"+name;
        assertFalse(BlockSafetyPolicy.mayBreak(id,false,5));
        assertTrue(BlockSafetyPolicy.mayBreak(id,false,5,true));
        assertFalse(BlockSafetyPolicy.mayBreak(id,false,5,false));
        assertFalse(BlockSafetyPolicy.disposable(id));
        assertFalse(BlockSafetyPolicy.mayBreak(id,true,5,true));
        assertFalse(BlockSafetyPolicy.mayBreak(id,false,-1,true));
    }
    @ParameterizedTest @ValueSource(strings={"chest","barrel","shulker_box","furnace","beacon","spawner"})
    void explicitAnvilExceptionNeverAllowsProtectedBlockEntities(String name) {
        assertFalse(BlockSafetyPolicy.mayBreak("minecraft:"+name,true,3,true));
    }
    @Test void explicitExceptionDoesNotApplyToModdedNamesOrValuables() {
        assertFalse(BlockSafetyPolicy.mayBreak("example:anvil",false,5,true));
        assertFalse(BlockSafetyPolicy.mayBreak("minecraft:diamond_block",false,5,true));
        assertFalse(BlockSafetyPolicy.mayBreak("minecraft:bedrock",false,-1,true));
        assertTrue(BlockSafetyPolicy.mayBreak("minecraft:iron_ore",false,3,true));
    }
    @Test void completionNotifiesOnceAndCancellationNeverSendsStaleResult() {
        int[] callbacks={0};var tasks=new TaskController(()->{});
        var first=new Probe();tasks.set(first,()->callbacks[0]++);tasks.stop();tasks.tick();assertEquals(0,callbacks[0]);
        var second=new Probe();tasks.set(second,()->callbacks[0]++);second.done=true;tasks.tick();tasks.tick();assertEquals(1,callbacks[0]);
        tasks.set(new Probe());tasks.stop();assertEquals(1,callbacks[0]);
    }
    private static class Probe implements CompanionTask {
        boolean done;
        public void tick() {} public void cancel() {done=true;} public boolean isComplete(){return done;} public String getStatus(){return "result";}
    }
}
