package dev.companions;

import dev.companions.navigation.flight.MaceApproach;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaceApproachTest {
    @Test void wallBesideMobStillOffersClearAttackSpots(){
        Vec3 chicken=new Vec3(0.5,64,0.5),flyer=new Vec3(-8,70,0.5);
        // A solid wall begins immediately east of the chicken; descent at
        // its center is unsafe, while western standing spots remain open.
        var spots=MaceApproach.candidates(chicken,flyer,
            point->point.x< -0.25&&Math.abs(point.z-chicken.z)<2.5);
        assertFalse(spots.isEmpty());
        for(Vec3 spot:spots){
            assertTrue(spot.x< -0.25);
            assertTrue(Math.hypot(spot.x-chicken.x,spot.z-chicken.z)<=2.251);
        }
        assertEquals(spots,MaceApproach.candidates(chicken,flyer,
            point->point.x< -0.25&&Math.abs(point.z-chicken.z)<2.5));
        assertTrue(spots.size()>1,"a blocked first approach should have an alternate");
    }
    @Test void noClearStandingSpotReturnsNoFalseApproach(){
        assertTrue(MaceApproach.candidates(new Vec3(0,64,0),new Vec3(3,68,0),point->false).isEmpty());
    }
}
