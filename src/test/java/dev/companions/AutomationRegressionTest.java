package dev.companions;

import dev.companions.crafting.*;
import dev.companions.inventory.*;
import dev.companions.navigation.*;
import dev.companions.navigation.path.Cell;
import dev.companions.world.ResourceClaims;
import dev.companions.persistence.CompanionStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomationRegressionTest {
    @BeforeAll static void bootstrap(){VanillaCraftingDataTest.bootstrap();}
    private static List<ItemStack> inventory(){var inv=new ArrayList<ItemStack>();for(int i=0;i<36;i++)inv.add(ItemStack.EMPTY);return inv;}
    private static long pos(int x,int y,int z){return BlockPos.asLong(x,y,z);}
    private static UUID bot(int id){return new UUID(0,id);}

    @ParameterizedTest @ValueSource(strings={"diamond_sword","dirt","oak_log","diamond_pickaxe","apple","crafting_table"})
    void workstationInteractionPreservesAnyHeldItemEvenWithNoFreeSlot(String name){
        var inv=inventory();for(int i=0;i<36;i++)inv.set(i,new ItemStack(Items.STONE,64));
        var held=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(name)));inv.set(4,held);
        assertEquals("opened",HeldItemInteraction.emptyHand(inv,4,()->{assertTrue(inv.get(4).isEmpty());return "opened";},s->fail("Unexpected received item")));
        assertSame(held,inv.get(4));assertEquals(64,inv.get(5).getCount());
    }
    @Test void deniedOrThrowingInteractionRestoresHeldItemAndPreservesHookOutput(){
        var inv=inventory();var sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamageValue(61);inv.set(0,sword);
        var received=new ArrayList<ItemStack>();
        assertThrows(IllegalStateException.class,()->HeldItemInteraction.emptyHand(inv,0,()->{inv.set(0,new ItemStack(Items.BUCKET));throw new IllegalStateException("hook denied");},received::add));
        assertSame(sword,inv.get(0));assertEquals(61,sword.getDamageValue());assertEquals(1,received.size());assertTrue(received.getFirst().is(Items.BUCKET));
    }
    @ParameterizedTest @ValueSource(strings={"wooden_pickaxe","stone_pickaxe","iron_pickaxe","diamond_pickaxe","crafting_table","furnace","chest","stick","iron_block"})
    void grantsExactlyMissingInputsForActualVanillaRecipe(String name)throws Exception{
        var recipe=new VanillaCraftingDataTest().recipe(name);var ingredients=recipe.placementInfo().ingredients();
        var inv=inventory();inv.set(0,new ItemStack(Items.DIAMOND_SWORD));inv.set(12,new ItemStack(Items.STICK));
        var missing=RecipeMaterials.missing(ingredients,inv);assertNotNull(missing);assertTrue(missing.size()<=9);
        assertTrue(MaterialGrants.insert(inv,missing));assertTrue(RecipeMaterials.missing(ingredients,inv).isEmpty());
        int[] counts=inv.stream().mapToInt(ItemStack::getCount).toArray();
        var slots=IngredientAllocator.allocate(ingredients.size(),counts,(n,s)->ingredients.get(n).test(inv.get(s)));assertNotNull(slots);
        for(int slot:slots)inv.get(slot).shrink(1);
        assertTrue(inv.get(0).is(Items.DIAMOND_SWORD));
        assertEquals(2+missing.size()-ingredients.size(),inv.stream().mapToInt(ItemStack::getCount).sum());
    }
    @Test void overlappingIngredientsUseExistingItemsBeforeGrantingAny(){
        var ingredients=List.of(Ingredient.of(Items.OAK_PLANKS,Items.BIRCH_PLANKS),Ingredient.of(Items.OAK_PLANKS));
        var inv=List.of(new ItemStack(Items.OAK_PLANKS),new ItemStack(Items.BIRCH_PLANKS));
        assertTrue(RecipeMaterials.missing(ingredients,inv).isEmpty());
        assertEquals(1,RecipeMaterials.missing(List.of(Ingredient.of(Items.OAK_PLANKS),Ingredient.of(Items.OAK_PLANKS)),List.of(new ItemStack(Items.OAK_PLANKS))).size());
    }
    @Test void grantsAreAtomicWhenFullAndRespectStackLimits(){
        var inv=new ArrayList<>(List.of(new ItemStack(Items.DIRT,63)));
        assertFalse(MaterialGrants.insert(inv,List.of(new ItemStack(Items.DIRT,2))));assertEquals(63,inv.getFirst().getCount());
        inv.add(ItemStack.EMPTY);assertTrue(MaterialGrants.insert(inv,List.of(new ItemStack(Items.DIRT,2))));assertEquals(64,inv.getFirst().getCount());assertEquals(1,inv.get(1).getCount());
    }
    @Test void materialChecksWaitFiveSecondsAndDoNotRepeatWithinTick(){
        var clock=new MaterialCheckClock();assertTrue(clock.due(20));
        for(int t=20;t<120;t++)assertFalse(clock.due(t));assertTrue(clock.due(120));assertFalse(clock.due(120));assertTrue(clock.due(500));assertFalse(clock.due(599));assertTrue(clock.due(600));
    }
    @Test void aFullInventoryCanCraftWhenConsumedIngredientsFreeAnOutputSlot(){
        var inv=inventory();for(int i=0;i<36;i++)inv.set(i,new ItemStack(Items.STONE,64));
        inv.set(12,new ItemStack(Items.OAK_PLANKS,2));
        assertTrue(RecipeMaterials.roomAfterConsumption(inv,new int[]{12,12},new ItemStack(Items.STICK,4)));
        assertEquals(2,inv.get(12).getCount());
        inv.get(12).grow(1);assertFalse(RecipeMaterials.roomAfterConsumption(inv,new int[]{12,12},new ItemStack(Items.STICK,4)));
    }
    @Test void outputCapacityRequiresSpaceForTheWholeRecipeBatch(){
        var inv=new ArrayList<>(List.of(new ItemStack(Items.STICK,63),new ItemStack(Items.OAK_PLANKS,64)));
        assertFalse(RecipeMaterials.roomAfterConsumption(inv,new int[]{1,1},new ItemStack(Items.STICK,4)));
        inv.getFirst().setCount(60);assertTrue(RecipeMaterials.roomAfterConsumption(inv,new int[]{1,1},new ItemStack(Items.STICK,4)));
    }
    @Test void guardStarterDirtIsIssuedOnceAndNeverRefilledWhenUsed(){
        var inv=inventory();inv.set(0,new ItemStack(Items.DIAMOND_SWORD));
        boolean issued=GuardSupplies.issueOnce(inv,false);assertTrue(issued);assertEquals(64,inv.stream().filter(s->s.is(Items.DIRT)).mapToInt(ItemStack::getCount).sum());
        inv.removeIf(s->s.is(Items.DIRT));assertTrue(GuardSupplies.issueOnce(inv,issued));assertTrue(inv.stream().noneMatch(s->s.is(Items.DIRT)));
    }
    @Test void guardFullInventoryDoesNotMarkUnreceivedDirtAsIssued(){
        var inv=new ArrayList<>(List.of(new ItemStack(Items.DIAMOND_SWORD)));assertFalse(GuardSupplies.issueOnce(inv,false));
        inv.add(ItemStack.EMPTY);assertTrue(GuardSupplies.issueOnce(inv,false));assertEquals(64,inv.get(1).getCount());
    }
    @Test void starterSupplyFlagSurvivesMetadataRoundTripAndOldRecordsDefaultFalse(){
        var gson=new com.google.gson.Gson();var old=gson.fromJson("{\"version\":1}",CompanionStore.Record.class);assertFalse(old.guardDirtIssued());
        var issued=gson.fromJson("{\"version\":1,\"guardDirtIssued\":true}",CompanionStore.Record.class);assertTrue(gson.fromJson(gson.toJson(issued),CompanionStore.Record.class).guardDirtIssued());
    }
    @Test void fourMinersReserveSeparateFiveBlockWorkZones(){
        var claims=new ResourceClaims();
        for(int i=0;i<4;i++)assertTrue(claims.claimWork(bot(i),"overworld",pos(i*5,64,0),0));
        assertFalse(claims.claimWork(bot(1),"overworld",pos(0,64,0),1));assertFalse(claims.claimWork(bot(1),"overworld",pos(4,64,0),1));
        assertTrue(claims.claimWork(bot(1),"overworld",pos(5,64,0),1));
        assertFalse(claims.workFrom(bot(1),"overworld",pos(1,64,0),1));assertTrue(claims.workFrom(bot(1),"overworld",pos(5,64,0),1));
        claims.release(bot(0));assertTrue(claims.claimWork(bot(1),"overworld",pos(0,64,0),2));
    }
    @Test void workSpacingIncludesNegativeCoordinatesHeightAndDimension(){
        var claims=new ResourceClaims();assertTrue(claims.claimWork(bot(0),"overworld",pos(-5,-32,-5),0));
        assertFalse(claims.claimWork(bot(1),"overworld",pos(-5,-28,-5),1));assertTrue(claims.claimWork(bot(1),"overworld",pos(-5,-27,-5),1));
        assertTrue(claims.claimWork(bot(2),"nether",pos(-5,-32,-5),1));assertTrue(claims.claimWork(bot(3),"overworld",pos(-5,-32,-5),200));
    }
    @Test void navigationBreakingAndMiningCannotBreakTheSameBlockConcurrently(){
        var claims=new ResourceClaims();long p=pos(9,40,9);
        assertTrue(claims.startBreaking(bot(0),"overworld",p,0));assertFalse(claims.startBreaking(bot(1),"overworld",p,1));assertFalse(claims.claimWork(bot(1),"overworld",p,1));
        claims.stopBreaking(bot(0));assertTrue(claims.claimWork(bot(1),"overworld",p,2));assertFalse(claims.startBreaking(bot(0),"overworld",p,3));
        assertTrue(claims.startBreaking(bot(1),"overworld",p,3));claims.release(bot(1));assertTrue(claims.startBreaking(bot(0),"overworld",p,4));
    }
    @Test void bridgeApproachOverhangRetainsPhysicalSupportInEveryDirection(){
        var source=new Cell(0,64,0);
        for(var direction:List.of(new Cell(1,63,0),new Cell(-1,63,0),new Cell(0,63,1),new Cell(0,63,-1))){
            var p=PlacementApproach.bridgeEdge(source,direction,64);
            assertTrue(p.x-0.3<1&&p.x+0.3>0&&p.z-0.3<1&&p.z+0.3>0);
            assertTrue(p.x<0||p.x>1||p.z<0||p.z>1);assertEquals(64,p.y);
        }
    }
}
