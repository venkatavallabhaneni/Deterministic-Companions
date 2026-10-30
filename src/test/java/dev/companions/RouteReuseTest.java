package dev.companions;

import dev.companions.navigation.path.*;
import dev.companions.navigation.action.PathAction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteReuseTest {
    @Test void targetRepairKeepsCurrentActionsAndConnectsToNewTarget(){
        Deque<PathEdge> route=new ArrayDeque<>();
        for(int x=1;x<=20;x++)route.add(PathEdge.move(new Cell(x,0,0),PathAction.MOVE));
        var first=route.getFirst();
        RouteReuse.keepPrefix(route,6);
        assertSame(first,route.getFirst());assertEquals(6,route.size());
        var anchor=route.getLast().destination();var goal=new Cell(6,0,8);
        var repair=new BoundedAStar(anchor,goal,0,32,2048,p->List.of(PathEdge.move(p.add(0,0,1),PathAction.MOVE)));
        repair.step(2);
        // Movement continues while the bounded repair is incomplete.
        assertFalse(repair.done());route.removeFirst();route.removeFirst();
        while(!repair.done())repair.step(2);
        route.addAll(repair.path());
        assertEquals(new Cell(3,0,0),route.getFirst().destination());
        assertEquals(goal,route.getLast().destination());
        Cell previous=new Cell(2,0,0);
        for(var edge:route){assertEquals(1,previous.distance(edge.destination()),0.001);previous=edge.destination();}
    }
    @Test void repairDoesNotDiscardBridgeOrJumpActions(){
        var bridge=new PathEdge(new Cell(1,1,0),PathAction.BRIDGE_FORWARD,List.of(),new Cell(1,0,0),4);
        Deque<PathEdge> route=new ArrayDeque<>(List.of(bridge,PathEdge.move(new Cell(2,2,0),PathAction.JUMP)));
        RouteReuse.keepPrefix(route,6);
        assertSame(bridge,route.getFirst());assertEquals(2,route.size());
    }
}
