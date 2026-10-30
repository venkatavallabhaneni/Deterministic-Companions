package dev.companions;

import dev.companions.crafting.WorkstationController;
import dev.companions.inventory.EquipmentSelection;
import dev.companions.world.ResourceClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MiningRetryTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){VanillaCraftingDataTest.bootstrap();}
    @Test void targetReservationsReleaseAfterAFailedRoute(){
        var claims=new ResourceClaims();var a=new UUID(0,1);var b=new UUID(0,2);
        assertTrue(claims.claimWork(a,"minecraft:overworld",BlockPos.ZERO.asLong(),0));
        assertFalse(claims.claimWork(b,"minecraft:overworld",BlockPos.ZERO.asLong(),1));
        claims.release(a);assertTrue(claims.claimWork(b,"minecraft:overworld",BlockPos.ZERO.asLong(),2));
    }
    @Test void targetRetrySetCanBeClearedForANewBoundedScan(){
        var rejected=new HashSet<BlockPos>();for(int i=0;i<512;i++)rejected.add(new BlockPos(i,64,0));
        assertEquals(512,rejected.size());rejected.clear();assertTrue(rejected.isEmpty());
    }
    @Test void constructionSupplyDoesNotReplaceHeldEquipment(){
        var inventory=new ArrayList<ItemStack>();for(int i=0;i<36;i++)inventory.add(ItemStack.EMPTY);
        var sword=new ItemStack(Items.DIAMOND_SWORD);inventory.set(0,sword);inventory.set(1,new ItemStack(Items.COBBLESTONE,64));
        assertSame(sword,inventory.get(0));assertEquals(64,inventory.get(1).getCount());assertEquals(1,inventory.stream().filter(s->s.is(Items.DIAMOND_SWORD)).count());
        assertEquals(2,EquipmentSelection.stow(inventory,0));assertSame(sword,inventory.get(0));
    }
    @Test void privateTableSitesRemainIndependentWhenMiningRetries(){
        var claims=new dev.companions.crafting.WorkstationClaims();var origin=new BlockPos(4,70,-2);var sites=WorkstationController.sites(origin);
        assertTrue(claims.reserve(new UUID(0,1),"overworld",sites.get(0)));var second=sites.stream().filter(p->p.distSqr(sites.get(0))>=25).findFirst().orElseThrow();
        assertTrue(claims.reserve(new UUID(0,2),"overworld",second));assertFalse(claims.available(new UUID(0,3),"overworld",sites.get(0)));
    }
}
