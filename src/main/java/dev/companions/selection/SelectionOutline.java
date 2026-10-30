package dev.companions.selection;

import dev.companions.combat.GuardArea;
import java.util.*;

/** Bounded particle outline: all twelve cuboid edges, at most 204 points. */
public final class SelectionOutline {
    public record Point(double x,double y,double z){}
    public static List<Point> points(GuardArea a){
        var result=new LinkedHashSet<Point>();double[] low={a.minX(),a.minY(),a.minZ()},high={(double)a.maxX()+1,(double)a.maxY()+1,(double)a.maxZ()+1};
        for(int axis=0;axis<3;axis++)for(int b=0;b<2;b++)for(int c=0;c<2;c++){
            int other=(axis+1)%3,last=(axis+2)%3;int steps=Math.min(16,(int)Math.ceil(high[axis]-low[axis]));
            for(int n=0;n<=steps;n++){double[] v=low.clone();v[axis]=low[axis]+(high[axis]-low[axis])*n/steps;v[other]=b==0?low[other]:high[other];v[last]=c==0?low[last]:high[last];result.add(new Point(v[0],v[1],v[2]));}
        }
        return List.copyOf(result);
    }
}
