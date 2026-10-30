package dev.companions;

import dev.companions.crafting.*;
import dev.companions.inventory.*;
import dev.companions.persistence.CompanionStore;
import dev.companions.world.BlockSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivateCraftingTest {
    @BeforeAll static void bootstrap(){VanillaCraftingDataTest.bootstrap();}
    private static UUID bot(int n){return new UUID(0,n);}
    private static ArrayList<ItemStack> inventory(){var inv=new ArrayList<ItemStack>();for(int i=0;i<36;i++)inv.add(ItemStack.EMPTY);return inv;}
    @Test void fourBotsChooseDistinctSeparatedTableSites(){
        var claims=new WorkstationClaims();var sites=WorkstationController.sites(new BlockPos(0,64,0));var chosen=new ArrayList<BlockPos>();
        for(int i=0;i<4;i++){
            UUID owner=bot(i);var p=sites.stream().filter(s->claims.available(owner,"overworld",s)).findFirst().orElseThrow();
            assertTrue(claims.reserve(owner,"overworld",p));for(var other:chosen)assertTrue(p.distSqr(other)>=25);chosen.add(p);
        }
        assertEquals(4,new HashSet<>(chosen).size());assertFalse(claims.reserve(bot(0),"overworld",chosen.get(1)));
        assertTrue(claims.reserve(bot(0),"overworld",chosen.get(0)));
    }
    @Test void tableClaimsReleaseOnlyTheirOwnerAndAreDimensionScoped(){
        var claims=new WorkstationClaims();var p=new BlockPos(-20,12,-8);
        assertTrue(claims.reserve(bot(0),"overworld",p));assertTrue(claims.reserve(bot(1),"nether",p));
        claims.release(bot(1));assertFalse(claims.available(bot(2),"overworld",p));
        claims.release(bot(0));assertTrue(claims.reserve(bot(2),"overworld",p));
    }
    @Test void changingATableSiteReleasesTheOldReservation(){
        var claims=new WorkstationClaims();assertTrue(claims.reserve(bot(0),"overworld",BlockPos.ZERO));
        assertTrue(claims.reserve(bot(0),"overworld",new BlockPos(100,0,0)));assertTrue(claims.available(bot(1),"overworld",BlockPos.ZERO));
    }
    @Test void workstationCandidateSearchIsBoundedUniqueAndNotUnderTheBot(){
        var origin=new BlockPos(-9,-12,41);var sites=WorkstationController.sites(origin);
        assertEquals(1400,sites.size());assertEquals(sites.size(),new HashSet<>(sites).size());
        double last=-1;for(var p:sites){assertTrue(p.distSqr(origin)>=last);last=p.distSqr(origin);
            assertTrue(Math.abs(p.getY()-origin.getY())<=2);int radius=Math.max(Math.abs(p.getX()-origin.getX()),Math.abs(p.getZ()-origin.getZ()));assertTrue(radius>=2&&radius<=8);}
        assertThrows(UnsupportedOperationException.class,()->sites.add(origin));
    }
    @Test void savedTableCoordinatesRoundTripAndOldMetadataLoadsWithoutTable(){
        var gson=new com.google.gson.Gson();assertNull(gson.fromJson("{\"version\":1}",CompanionStore.Record.class).table());
        var saved=new WorkstationController.SavedTable("minecraft:overworld",-123,80,99);
        var record=gson.fromJson("{\"version\":1,\"table\":"+gson.toJson(saved)+"}",CompanionStore.Record.class);
        assertEquals(saved,gson.fromJson(gson.toJson(record),CompanionStore.Record.class).table());
    }
    @ParameterizedTest @ValueSource(ints={0,4,8})
    void stowingUsesAnEmptyHotbarSlotWithoutMovingOrLosingHeldEquipment(int selected){
        var inv=inventory();var sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamageValue(123);inv.set(selected,sword);
        int empty=EquipmentSelection.stow(inv,selected);assertTrue(inv.get(empty).isEmpty());assertSame(sword,inv.get(selected));assertEquals(123,sword.getDamageValue());
    }
    @Test void fullHotbarStowsIntoStorageAndClearsMainHand(){
        var inv=inventory();for(int i=0;i<9;i++)inv.set(i,new ItemStack(Items.STONE,64));
        var tool=new ItemStack(Items.IRON_PICKAXE);inv.set(3,tool);
        assertEquals(3,EquipmentSelection.stow(inv,3));assertTrue(inv.get(3).isEmpty());assertSame(tool,inv.get(9));
        assertEquals(3,EquipmentSelection.stow(inv,3));assertSame(tool,inv.get(9));
    }
    @Test void fullInventoryStowDoesNotDestroyOrDuplicateEquipment(){
        var inv=inventory();for(int i=0;i<36;i++)inv.set(i,new ItemStack(Items.STONE,64));
        var tool=new ItemStack(Items.DIAMOND_PICKAXE);inv.set(2,tool);assertEquals(2,EquipmentSelection.stow(inv,2));assertSame(tool,inv.get(2));
        assertEquals(1,inv.stream().filter(s->s.is(Items.DIAMOND_PICKAXE)).count());
    }
    @Test void eachBotBootstrapsItsTableFromSeparateSuppliedIngredients()throws Exception{
        var recipe=new VanillaCraftingDataTest().recipe("crafting_table");var inputs=recipe.placementInfo().ingredients();
        var inventories=new ArrayList<List<ItemStack>>();
        for(int b=0;b<4;b++){
            var inv=inventory();inv.set(0,new ItemStack(Items.DIAMOND_SWORD));inventories.add(inv);
            var missing=RecipeMaterials.missing(inputs,inv);assertEquals(4,missing.size());assertTrue(MaterialGrants.insert(inv,missing));
            var counts=inv.stream().mapToInt(ItemStack::getCount).toArray();var slots=IngredientAllocator.allocate(inputs.size(),counts,(n,s)->inputs.get(n).test(inv.get(s)));
            assertNotNull(slots);var grid=new ArrayList<ItemStack>();for(int slot:slots)grid.add(inv.get(slot).split(1));
            var input=CraftingInput.of(2,2,grid);assertTrue(recipe.matches(input,null));var result=recipe.assemble(input);assertTrue(result.is(Items.CRAFTING_TABLE));
            assertTrue(MaterialGrants.insert(inv,List.of(result)));assertTrue(inv.get(0).is(Items.DIAMOND_SWORD));
        }
        inventories.get(0).clear();for(int i=1;i<4;i++)assertEquals(1,inventories.get(i).stream().filter(s->s.is(Items.CRAFTING_TABLE)).mapToInt(ItemStack::getCount).sum());
    }
    @Test void independentBotsMaterialChecksDoNotStarveEachOther(){
        var clocks=List.of(new MaterialCheckClock(),new MaterialCheckClock(),new MaterialCheckClock(),new MaterialCheckClock());
        for(var clock:clocks){assertTrue(clock.due(0));assertFalse(clock.due(99));assertTrue(clock.due(100));}
    }
    @ParameterizedTest @ValueSource(ints={-150,-100,-65,65,100,150})
    void expandedScanIncludesDistantBlocksOnEveryAxis(int distance){
        var p=new BlockPos(-77,0,11);assertTrue(BlockSearch.inRange(p,p.offset(distance,0,0)));assertTrue(BlockSearch.inRange(p,p.offset(0,distance,0)));assertTrue(BlockSearch.inRange(p,p.offset(0,0,distance)));
    }
    @Test void xyzVolumeIncludesCornersButRejectsAnyAxisOutside150(){
        assertEquals(150,BlockSearch.RADIUS);assertTrue(BlockSearch.inRange(BlockPos.ZERO,new BlockPos(150,-150,150)));
        for(var p:List.of(new BlockPos(151,0,0),new BlockPos(0,-151,0),new BlockPos(0,0,151)))assertFalse(BlockSearch.inRange(BlockPos.ZERO,p));
        assertFalse(BlockSearch.inRange(new BlockPos(Integer.MIN_VALUE,0,0),new BlockPos(Integer.MAX_VALUE,0,0)));
    }
}
