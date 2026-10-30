package dev.companions.combat;

import net.minecraft.world.phys.Vec3;

/** Detects sustained lack of movement only while movement is actually required. */
public final class ProgressWatchdog {
    private final int limit;
    private Vec3 last;
    private int stagnant;
    public ProgressWatchdog(int limit){this.limit=limit;}
    public boolean tick(Vec3 position,boolean movementRequired){
        if(!movementRequired){last=position;stagnant=0;return false;}
        if(last==null){last=position;stagnant=1;return limit<=1;}
        if(position.distanceToSqr(last)>0.01){last=position;stagnant=0;return false;}
        if(++stagnant<limit)return false;
        stagnant=0;last=position;return true;
    }
}
