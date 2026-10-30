package dev.companions.navigation.flight;

import dev.companions.navigation.WorldNeighbors;
import dev.companions.player.CompanionController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

/** Collision and hazard samples use the live server world, never a worker thread. */
public final class FlightAirspace implements FlightPathfinder.Air {
    private final CompanionController c;
    public FlightAirspace(CompanionController c){this.c=c;}
    @Override public boolean clear(Vec3 point){
        var player=c.player;
        var level=player.level();
        BlockPos feet=BlockPos.containing(point),head=feet.above();
        if(!level.hasChunkAt(feet)||!level.hasChunkAt(head)||level.isOutsideBuildHeight(feet)
            ||level.isOutsideBuildHeight(head)||!level.getWorldBorder().isWithinBounds(feet)
            ||c.navigation.world.hazard(WorldNeighbors.cell(feet))
            ||c.navigation.world.hazard(WorldNeighbors.cell(head)))return false;
        Vec3 movement=point.subtract(player.position());
        return level.noBlockCollision(player,player.getBoundingBox().move(movement).inflate(0.6,0.6,0.6));
    }
    /** Full-height player clearance and solid support for a controlled landing. */
    public boolean landable(Vec3 feet){
        var level=c.player.level();
        BlockPos at=BlockPos.containing(feet);
        if(!level.hasChunkAt(at)||level.isOutsideBuildHeight(at)||!level.getWorldBorder().isWithinBounds(at)
            ||c.navigation.world.hazard(WorldNeighbors.cell(at))
            ||c.navigation.world.hazard(WorldNeighbors.cell(at.above())))return false;
        AABB standing=new AABB(feet.x-0.32,feet.y+0.01,feet.z-0.32,
            feet.x+0.32,feet.y+1.82,feet.z+0.32);
        if(!level.noBlockCollision(c.player,standing))return false;
        for(double dx:new double[]{-0.25,0.25})for(double dz:new double[]{-0.25,0.25}){
            BlockPos support=BlockPos.containing(feet.add(dx,-0.1,dz));
            if(!level.hasChunkAt(support)||c.navigation.world.hazard(WorldNeighbors.cell(support)))return false;
            var shape=level.getBlockState(support).getCollisionShape(level,support);
            if(shape.isEmpty()||shape.max(Direction.Axis.Y)<0.9)return false;
        }
        return true;
    }
}
