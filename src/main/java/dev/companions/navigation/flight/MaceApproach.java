package dev.companions.navigation.flight;

import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Deterministic standing attack spots around a target, ordered by approach distance. */
public final class MaceApproach {
    private static final int[][] DIRECTIONS={
        {1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}
    };
    private MaceApproach(){}
    public static List<Vec3> candidates(Vec3 target,Vec3 flyer,Predicate<Vec3> usable){
        record Choice(Vec3 point,int order){}
        List<Choice> choices=new ArrayList<>();
        int order=0;
        for(double radius:new double[]{1.75,2.25})for(int[] direction:DIRECTIONS){
            double normal=Math.hypot(direction[0],direction[1]);
            Vec3 point=target.add(direction[0]*radius/normal,0,direction[1]*radius/normal);
            if(usable.test(point))choices.add(new Choice(point,order));
            order++;
        }
        choices.sort(Comparator.comparingDouble((Choice choice)->choice.point.distanceToSqr(flyer))
            .thenComparingInt(Choice::order));
        return choices.stream().map(Choice::point).toList();
    }
}
