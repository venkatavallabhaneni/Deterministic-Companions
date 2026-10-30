package dev.companions.navigation;

import dev.companions.navigation.path.Cell;
import net.minecraft.world.phys.Vec3;

/** Crouch to an overhanging position while retaining 0.1 block of supporting overlap. */
public final class PlacementApproach {
    public static Vec3 bridgeEdge(Cell source,Cell placement,double feetY){
        int dx=Integer.signum(placement.x()-source.x()),dz=Integer.signum(placement.z()-source.z());
        return new Vec3(source.x()+0.5+dx*0.7,feetY,source.z()+0.5+dz*0.7);
    }
}
