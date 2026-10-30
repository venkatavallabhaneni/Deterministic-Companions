package dev.companions.task;

import net.minecraft.world.phys.Vec3;

/** Limits per-tick heading changes so elytra flight follows smooth arcs. */
public final class FlightSteering {
    public record Heading(float yaw,float pitch){}
    private FlightSteering(){}
    public static Heading toward(Vec3 eye,Vec3 destination){
        Vec3 delta=destination.subtract(eye);
        double horizontal=Math.hypot(delta.x,delta.z);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,horizontal));
        return new Heading(yaw,pitch);
    }
    public static Heading step(Heading current,Heading desired){
        float yawDelta=wrap(desired.yaw-current.yaw);
        float pitchDelta=desired.pitch-current.pitch;
        return new Heading(current.yaw+Math.clamp(yawDelta,-6f,6f),
            current.pitch+Math.clamp(pitchDelta,-4f,4f));
    }
    public static Vec3 diveVelocity(Vec3 flyer,Vec3 target,double verticalSpeed){
        Vec3 correction=target.subtract(flyer);
        return new Vec3(Math.clamp(correction.x*0.22,-0.45,0.45),verticalSpeed,
            Math.clamp(correction.z*0.22,-0.45,0.45));
    }
    private static float wrap(float value){
        float wrapped=value%360f;
        if(wrapped>=180f)wrapped-=360f;
        if(wrapped< -180f)wrapped+=360f;
        return wrapped;
    }
}
