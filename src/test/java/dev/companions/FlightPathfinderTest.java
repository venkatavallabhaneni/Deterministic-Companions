package dev.companions;

import dev.companions.navigation.flight.FlightPathfinder;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightPathfinderTest {
    @Test void fliesOverOrAroundMountainInsteadOfThroughIt(){
        FlightPathfinder.Air ridge=p->!(p.x>=8&&p.x<=16&&Math.abs(p.z)<=12&&p.y<20);
        var planner=new FlightPathfinder(ridge);
        Vec3 start=new Vec3(0,5,0),goal=new Vec3(28,5,0);
        Vec3 waypoint=null;
        for(int tick=0;tick<100&&waypoint==null;tick++)waypoint=planner.next(start,goal,12);
        assertNotNull(waypoint,"bounded search should find an air route around the ridge");
        assertFalse(planner.failed());
        assertFalse(waypoint.x>=8&&waypoint.x<=16&&Math.abs(waypoint.z)<=12&&waypoint.y<20);
        assertTrue(planner.remaining()>0);
    }
    @Test void workPerCallIsBoundedAndBlockedRouteTerminates(){
        final int[] checks={0};
        FlightPathfinder.Air trapped=p->{checks[0]++;return p.distanceToSqr(new Vec3(0,10,0))<9;};
        var planner=new FlightPathfinder(trapped);
        Vec3 start=new Vec3(0,10,0),goal=new Vec3(24,10,0);
        assertNull(planner.next(start,goal,1));
        assertTrue(checks[0]<150,"one tick must not explore the entire search space");
        for(int tick=0;tick<100&&!planner.failed();tick++)planner.next(start,goal,12);
        assertTrue(planner.failed());
    }
    @Test void waypointsReachGoalAndAvoidCliffAfterMovement(){
        FlightPathfinder.Air cliff=p->!(p.x>=8&&p.x<=16&&Math.abs(p.z)<=8&&p.y<17);
        var planner=new FlightPathfinder(cliff);
        Vec3 current=new Vec3(0,5,0),goal=new Vec3(28,5,0);
        for(int step=0;step<80&&current.distanceToSqr(goal)>6.25;step++){
            Vec3 waypoint=null;
            for(int tick=0;tick<80&&waypoint==null;tick++)waypoint=planner.next(current,goal,12);
            assertNotNull(waypoint);
            assertTrue(cliff.clear(waypoint));
            current=waypoint;
        }
        assertTrue(current.distanceToSqr(goal)<=6.25,"cached route should carry the flyer around the cliff");
    }
    @Test void patrolGoalEmbeddedInMountainIsLiftedAboveIt(){
        var planner=new FlightPathfinder(p->p.y>=30);
        assertEquals(33,planner.liftGoal(new Vec3(4,9,4)).y,0.001);
    }
    @Test void closeTargetCanUseTwoBlockAirCellsBesideWall(){
        FlightPathfinder.Air wall=p->!(p.x>=4&&p.x<=8&&Math.abs(p.z-2)>0.9);
        var planner=new FlightPathfinder(wall);
        Vec3 start=new Vec3(0,64,0),goal=new Vec3(12,64,0);
        Vec3 waypoint=null;
        for(int tick=0;tick<100&&waypoint==null;tick++)waypoint=planner.next(start,goal,12);
        assertNotNull(waypoint,"a two-block detour should pass through the side opening");
        assertFalse(planner.failed());
    }
}
