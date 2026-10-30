package dev.companions.navigation;

import dev.companions.navigation.path.*;
import dev.companions.navigation.action.PathAction;
import java.util.*;

/** One legitimate inventory placement per edge; execution rechecks and consumes the actual block. */
public final class ConstructionMoves {
    public interface Terrain {boolean clear(Cell p);boolean support(Cell p);boolean replaceable(Cell p);boolean safe(Cell p);}
    public static List<PathEdge> horizontal(Cell from,Cell to,int blocks,Terrain t){
        if(blocks<1||!t.safe(to)||!t.safe(to.add(0,1,0)))return List.of();
        var out=new ArrayList<PathEdge>(2);Cell floor=to.add(0,-1,0),up=to.add(0,1,0);
        // Keep this option even if there is a safe floor several blocks below the gap.
        if(t.clear(to)&&t.safe(floor)&&!t.support(floor)&&t.replaceable(floor))out.add(new PathEdge(to,PathAction.BRIDGE_FORWARD,List.of(),floor,0));
        if(t.support(floor)&&t.replaceable(to)&&t.clear(up)&&t.clear(from.add(0,1,0)))out.add(new PathEdge(up,PathAction.PLACE,List.of(),to,0));
        return out;
    }
    public static List<PathEdge> pillar(Cell from,int blocks,Terrain t){return pillar(from,blocks,t,false);}
    /** @param placedBelow the route reaching from already places a block under it, so pillars chain in one search */
    public static List<PathEdge> pillar(Cell from,int blocks,Terrain t,boolean placedBelow){
        if(blocks>0&&t.safe(from)&&t.clear(from.add(0,1,0))&&(placedBelow||t.support(from.add(0,-1,0)))&&t.replaceable(from))
            return List.of(new PathEdge(from.add(0,1,0),PathAction.PILLAR_UP,List.of(),from,0));
        return List.of();
    }
}
