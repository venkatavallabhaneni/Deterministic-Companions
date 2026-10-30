package dev.companions.navigation;

import dev.companions.world.BlockSafetyPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Physical collision, never selectable outlines or an air-only whitelist. */
public final class TraversalGeometry {
    private TraversalGeometry(){}
    public static boolean blocksMovement(BlockGetter world,BlockPos pos){return !world.getBlockState(pos).getCollisionShape(world,pos).isEmpty();}
    public static boolean clearBody(CollisionGetter world,Entity player,BlockPos feet,double floorY){
        for(BlockPos pos:new BlockPos[]{feet,feet.above()})
            if(BlockSafetyPolicy.dangerous(BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).toString()))return false;
        // A* describes traversable world geometry. Other companions and mobs
        // are transient obstacles and must not erase every outgoing edge from
        // a bot that is temporarily boxed in on a narrow bridge. Physical
        // movement still uses vanilla entity collision, and stuck recovery
        // waits/replans if that entity has not moved out of the way.
        return world.noBlockCollision(player,new AABB(feet.getX()+0.2,floorY+0.01,feet.getZ()+0.2,feet.getX()+0.8,floorY+1.8,feet.getZ()+0.8));
    }
    public static boolean visible(BlockGetter world,Entity player,Vec3 eye,Vec3 hitPoint,BlockPos target){
        var context=player==null?net.minecraft.world.phys.shapes.CollisionContext.empty():net.minecraft.world.phys.shapes.CollisionContext.of(player);
        var hit=world.clip(new ClipContext(eye,hitPoint,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,context));
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(target);
    }
}
