package dev.companions;

import dev.companions.combat.*;
import dev.companions.config.*;
import dev.companions.inventory.IssuedSword;
import dev.companions.task.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuardFeaturesTest {
    @BeforeAll static void bootstrap(){VanillaCraftingDataTest.bootstrap();}
    @Test void reversedCornersIncludeWholeBoundaryBlocksAndThinArea(){
        var a=GuardArea.between(29,1,0,19,10,0);assertEquals(new GuardArea(19,1,0,29,10,0),a);
        assertTrue(a.contains(19,1,0));assertTrue(a.contains(29.99,10.99,0.99));assertFalse(a.contains(30,5,0));assertFalse(a.contains(25,5,1));assertFalse(a.contains(25,0.99,0));
    }
    @Test void areaSizeIsBoundedWithoutIntegerOverflow(){
        assertDoesNotThrow(()->GuardArea.between(0,0,0,127,127,127));
        assertThrows(IllegalArgumentException.class,()->GuardArea.between(0,0,0,128,0,0));
        assertThrows(IllegalArgumentException.class,()->GuardArea.between(Integer.MIN_VALUE,0,0,Integer.MAX_VALUE,0,0));
    }
    @Test void guardCountSelectionIsExactImmutableAndDoesNotModifyCandidates(){
        var available=new ArrayList<>(List.of("bob","alice","alex"));var chosen=GuardAssignment.choose(available,2);
        assertEquals(List.of("bob","alice"),chosen);assertEquals(3,available.size());assertThrows(UnsupportedOperationException.class,()->chosen.add("fourth"));
        assertThrows(IllegalArgumentException.class,()->GuardAssignment.choose(available,4));assertEquals(3,available.size());
        assertThrows(IllegalArgumentException.class,()->GuardAssignment.choose(available,0));assertThrows(IllegalArgumentException.class,()->GuardAssignment.choose(available,5));
    }
    @Test void healthValidationAndOldMetadataDefaults(){
        assertEquals(20,new CompanionConfig().maxHealth);assertEquals(40,HealthPolicy.validate(40));assertEquals(1024,HealthPolicy.validate(1024));
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,-1,1025})assertThrows(IllegalArgumentException.class,()->HealthPolicy.validate(bad));
        assertEquals(20,HealthPolicy.restore(0));assertEquals(20,HealthPolicy.restore(Float.NaN));assertEquals(1024,HealthPolicy.restore(5000));
        assertEquals(20,new com.google.gson.Gson().fromJson("{\"maxDistance\":256}",CompanionConfig.class).maxHealth);
    }
    @Test void issuedSwordUsesEmptySlotAndDoesNotDuplicateExistingSword(){
        var slots=new ArrayList<>(List.of(new ItemStack(Items.APPLE,3),ItemStack.EMPTY));var overflow=new ArrayList<ItemStack>();
        assertEquals(1,IssuedSword.ensure(slots,0,overflow::add));assertTrue(slots.get(1).is(Items.DIAMOND_SWORD));assertEquals(3,slots.get(0).getCount());
        ItemStack original=slots.get(1);original.setDamageValue(100);assertEquals(1,IssuedSword.ensure(slots,0,overflow::add));assertSame(original,slots.get(1));assertEquals(100,slots.get(1).getDamageValue());assertTrue(overflow.isEmpty());
    }
    @Test void fullInventoryPreservesDisplacedItemsInOverflow(){
        var old=new ItemStack(Items.DIAMOND,64);var slots=new ArrayList<>(List.of(old,new ItemStack(Items.APPLE)));var overflow=new ArrayList<ItemStack>();
        assertEquals(0,IssuedSword.ensure(slots,0,overflow::add));assertSame(old,overflow.getFirst());assertEquals(64,overflow.getFirst().getCount());assertTrue(slots.getFirst().is(Items.DIAMOND_SWORD));
    }
    @Test void replacingProtectThenStoppingGuardLeavesNoOldTaskRunning(){
        var protect=new CompanionLogicTest.Probe();var guard=new CompanionLogicTest.Probe();var tasks=new TaskController(()->{});
        tasks.set(protect);tasks.set(guard);tasks.tick();assertEquals(1,protect.cancels);assertSame(guard,tasks.current());
        tasks.stop();tasks.tick();assertEquals(1,guard.cancels);assertNull(tasks.current());assertEquals(1,guard.ticks);assertEquals(0,protect.ticks);
    }
}
