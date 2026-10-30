package dev.companions;

import com.google.gson.Gson;
import dev.companions.player.CompanionLimit;
import dev.companions.persistence.CompanionStore;
import dev.companions.selection.FlyingTargetStick;
import dev.companions.task.PatrolOrbit;
import dev.companions.task.FlightSteering;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FlyingCompanionTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void fifthSlotIsSeparateFromFourNormalSlots(){
        assertDoesNotThrow(()->CompanionLimit.check(3));
        assertThrows(IllegalStateException.class,()->CompanionLimit.check(4));
        assertDoesNotThrow(()->CompanionLimit.checkFlyer(0));
        assertEquals("Maximum flying companions reached: 1/1",
            assertThrows(IllegalStateException.class,()->CompanionLimit.checkFlyer(1)).getMessage());
    }
    @Test void legacySavedCompanionDoesNotBecomeFlyer(){
        var gson=new Gson();
        var old=gson.fromJson("{\"version\":1}",CompanionStore.Record.class);
        assertFalse(old.flyer());
        var flyer=gson.fromJson("{\"version\":1,\"flyer\":true}",CompanionStore.Record.class);
        assertTrue(gson.fromJson(gson.toJson(flyer),CompanionStore.Record.class).flyer());
    }
    @Test void elytraCanUseVanillaUnbreakableComponent(){
        var wings=new ItemStack(Items.ELYTRA);
        wings.set(DataComponents.UNBREAKABLE,Unit.INSTANCE);
        assertTrue(wings.is(Items.ELYTRA));
        assertEquals(Unit.INSTANCE,wings.get(DataComponents.UNBREAKABLE));
        assertEquals(64,new ItemStack(Items.FIREWORK_ROCKET,64).getCount());
    }
    @Test void patrolPostsCircleOwnerInsteadOfHoveringAbove(){
        Vec3 owner=new Vec3(100,64,-30);
        for(int i=0;i<PatrolOrbit.POSTS;i++){
            Vec3 post=PatrolOrbit.point(owner,i);
            assertEquals(PatrolOrbit.RADIUS,Math.hypot(post.x-owner.x,post.z-owner.z),1e-8);
            assertEquals(8,post.y-owner.y,1e-8);
            assertEquals(i,PatrolOrbit.nearest(owner,post));
            assertTrue(post.distanceToSqr(PatrolOrbit.point(owner,i+1))>1);
        }
    }
    @Test void flyingStickIsBoundToOneCompanionAndOrdinarySticksAreRejected(){
        UUID falcon=UUID.randomUUID(),other=UUID.randomUUID();
        assertEquals(falcon,FlyingTargetStick.target(FlyingTargetStick.create(falcon)));
        assertNotEquals(other,FlyingTargetStick.target(FlyingTargetStick.create(falcon)));
        assertNull(FlyingTargetStick.target(new ItemStack(Items.STICK)));
    }
    @Test void flightSteeringLimitsTurnsAndWrapsAcrossNorth(){
        var current=new FlightSteering.Heading(179,0);
        var next=FlightSteering.step(current,new FlightSteering.Heading(-179,-40));
        assertEquals(181,next.yaw(),0.001);
        assertEquals(-4,next.pitch(),0.001);
        var farther=FlightSteering.step(next,new FlightSteering.Heading(-90,20));
        assertTrue(Math.abs(farther.yaw()-next.yaw())<=6.001);
        assertTrue(Math.abs(farther.pitch()-next.pitch())<=4.001);
    }
    @Test void flightHeadingPointsTowardDestination(){
        var heading=FlightSteering.toward(new Vec3(0,0,0),new Vec3(0,0,10));
        assertEquals(0,heading.yaw(),0.001);
        assertEquals(0,heading.pitch(),0.001);
    }
    @Test void diveSteeringAlignsWithSmallTargetWithoutExcessHorizontalSpeed(){
        Vec3 flyer=new Vec3(2.9,6,-2.5),chicken=new Vec3(0,0,0);
        double initial=Math.hypot(flyer.x-chicken.x,flyer.z-chicken.z);
        for(int tick=0;tick<10;tick++){
            Vec3 velocity=FlightSteering.diveVelocity(flyer,chicken,-0.7);
            assertTrue(velocity.horizontalDistance()<=0.64);
            flyer=flyer.add(velocity);
        }
        assertTrue(Math.hypot(flyer.x-chicken.x,flyer.z-chicken.z)<initial*0.2);
    }
}
