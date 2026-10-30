package dev.companions.combat;

import net.minecraft.world.phys.Vec3;

/** Deterministic, separated approach points for groups of one to four bots. */
public final class FormationPlanner {
    private FormationPlanner() {}
    public static Vec3 point(Vec3 center,int slot,int count,double radius,int variant){
        int safeCount=Math.clamp(count,1,4),safeSlot=Math.floorMod(slot,safeCount);
        double angle=2*Math.PI*safeSlot/safeCount+Math.floorMod(variant,8)*Math.PI/8;
        double adjusted=radius+(Math.floorMod(variant,8)/4)*0.6;
        return center.add(Math.cos(angle)*adjusted,0,Math.sin(angle)*adjusted);
    }
}
