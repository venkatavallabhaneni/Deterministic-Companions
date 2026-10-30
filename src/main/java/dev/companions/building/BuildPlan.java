package dev.companions.building;

import dev.companions.navigation.path.Cell;
import java.util.*;

/** Immutable placements and explicit removals for built-in construction shapes. */
public record BuildPlan(String name,List<Cell> placements,Set<Cell> removals,String material) {
    public BuildPlan {placements=List.copyOf(placements);removals=Set.copyOf(removals);if(placements.size()>4096)throw new IllegalArgumentException("Plan exceeds 4096 blocks");}
    public static BuildPlan generate(String shape,Cell origin,int fx,int fz,int a,int b) {
        if(a<1||a>64||b<1||b>64||Math.abs(fx)+Math.abs(fz)!=1)throw new IllegalArgumentException("Invalid build dimensions or direction");
        List<Cell> out=new ArrayList<>(); int rx=-fz,rz=fx;
        switch(shape) {
            case "wall" -> {for(int y=0;y<b;y++)for(int x=0;x<a;x++)out.add(origin.add(fx+rx*x,y,fz+rz*x));}
            case "floor" -> {for(int z=1;z<=b;z++)for(int x=0;x<a;x++)out.add(origin.add(fx*z+rx*x,-1,fz*z+rz*x));}
            case "bridge" -> {for(int n=1;n<=a;n++)out.add(origin.add(fx*n,-1,fz*n));}
            case "pillar" -> {for(int y=0;y<a;y++)out.add(origin.add(0,y,0));}
            case "stairs" -> {for(int n=1;n<=a;n++)for(int y=0;y<n;y++)out.add(origin.add(fx*n,y,fz*n));}
            default -> throw new IllegalArgumentException("Unknown build shape: "+shape);
        }
        return new BuildPlan(shape,out,Set.of(),null);
    }
}
