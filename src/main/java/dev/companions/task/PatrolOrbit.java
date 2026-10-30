package dev.companions.task;

import net.minecraft.world.phys.Vec3;

/** Fixed-radius, deterministic patrol posts around a moving owner. */
public final class PatrolOrbit {
    public static final int POSTS=8;
    public static final double RADIUS=14;
    private PatrolOrbit(){}
    public static Vec3 point(Vec3 owner,int index){
        double angle=2*Math.PI*Math.floorMod(index,POSTS)/POSTS;
        return owner.add(Math.cos(angle)*RADIUS,8,Math.sin(angle)*RADIUS);
    }
    public static int nearest(Vec3 owner,Vec3 flyer){
        double angle=Math.atan2(flyer.z-owner.z,flyer.x-owner.x);
        return Math.floorMod((int)Math.round(angle*POSTS/(2*Math.PI)),POSTS);
    }
}
