package dev.companions;

import dev.companions.combat.FormationPlanner;
import dev.companions.combat.ProgressWatchdog;
import dev.companions.survival.SurvivalPolicy;
import dev.companions.navigation.TrackingRetargetPolicy;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtectRecoveryTest {
    @Test void fourProtectorsReceiveDistinctStableFormationPoints(){
        Vec3 center=new Vec3(10,64,-4);var points=new HashSet<Vec3>();
        for(int i=0;i<4;i++){Vec3 point=FormationPlanner.point(center,i,4,2.5,0);assertTrue(points.add(point));assertEquals(6.25,point.distanceToSqr(center),0.001);}
    }
    @Test void alternateRouteVariantsMoveAStalledApproach(){
        Vec3 center=Vec3.ZERO,first=FormationPlanner.point(center,1,4,2.5,0);
        for(int variant=1;variant<8;variant++)assertNotEquals(first,FormationPlanner.point(center,1,4,2.5,variant));
    }
    @Test void staleDamageCannotPermanentlyTakeControlFromProtect(){
        assertTrue(SurvivalPolicy.shouldRetreat(5,true,1000,901));
        assertFalse(SurvivalPolicy.shouldRetreat(5,true,1001,901));
        assertFalse(SurvivalPolicy.shouldRetreat(5,false,1000,999));
        assertFalse(SurvivalPolicy.shouldRetreat(6,true,1000,999));
    }
    @Test void watchdogRecoversOnlyAfterSustainedRequiredMovementFailure(){
        var watchdog=new ProgressWatchdog(4);Vec3 point=new Vec3(1,2,3);
        assertFalse(watchdog.tick(point,true));assertFalse(watchdog.tick(point,true));assertFalse(watchdog.tick(point,true));assertTrue(watchdog.tick(point,true));
        assertFalse(watchdog.tick(point,false));assertFalse(watchdog.tick(point,true));
        assertFalse(watchdog.tick(point.add(1,0,0),true));
    }
    @Test void routeSearchDoesNotBecomeAFalseFormationStall(){
        var watchdog=new ProgressWatchdog(4);Vec3 point=new Vec3(1,2,3);
        for(int tick=0;tick<20;tick++)assertFalse(watchdog.tick(point,false));
        assertFalse(watchdog.tick(point,true));
    }
    @Test void movingTargetDoesNotDiscardAnActiveSearch(){
        assertFalse(TrackingRetargetPolicy.shouldRetarget(10_000,true,false,false,0.8));
        assertFalse(TrackingRetargetPolicy.shouldRetarget(25,false,true,false,0.8));
        assertTrue(TrackingRetargetPolicy.shouldRetarget(49,false,true,false,0.8));
        assertTrue(TrackingRetargetPolicy.shouldRetarget(1,false,false,true,0.8));
    }
}
