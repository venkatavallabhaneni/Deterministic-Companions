package dev.companions;

import dev.companions.navigation.TraversalGeometry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual 26.2 collision/raycast algorithms over a small in-memory block fixture. No server world. */
class VegetationNavigationTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static class Fixture implements CollisionGetter {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        List<VoxelShape> entityCollisions=List.of();
        public BlockState getBlockState(BlockPos p){return blocks.getOrDefault(p,Blocks.AIR.defaultBlockState());}
        public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        public BlockEntity getBlockEntity(BlockPos p){return null;}
        public int getHeight(){return 384;}public int getMinY(){return -64;}
        public WorldBorder getWorldBorder(){return new WorldBorder();}
        public BlockGetter getChunkForCollisions(int x,int z){return this;}
        public List<VoxelShape> getEntityCollisions(Entity source,AABB box){return entityCollisions;}
        void put(BlockPos p,BlockState state){blocks.put(p,state);}
    }
    @ParameterizedTest @ValueSource(strings={"short_grass","fern","dead_bush","dandelion","poppy","oak_sapling","torch","rail","wheat","vine","tall_grass","large_fern","sunflower","lilac"})
    void safeNonSolidPlantsAndDecorationsDoNotBlockPlayerBody(String id){
        var world=new Fixture();BlockPos feet=new BlockPos(0,1,0);var state=BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).defaultBlockState();
        world.put(feet.below(),Blocks.STONE.defaultBlockState());world.put(feet,state);
        if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))world.put(feet.above(),state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
        assertFalse(TraversalGeometry.blocksMovement(world,feet),id);
        assertTrue(TraversalGeometry.clearBody(world,null,feet,1),id);
    }
    @Test void grassOutlineReproducesOldVisibilityFailureButColliderReachesTarget(){
        var world=new Fixture();var grass=new BlockPos(1,0,0);var target=new BlockPos(3,0,0);
        world.put(grass,Blocks.TALL_GRASS.defaultBlockState());world.put(grass.above(),Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));world.put(target,Blocks.STONE.defaultBlockState());
        var eye=new Vec3(0.5,0.6,0.5);var point=new Vec3(3.5,0.6,0.5);
        assertEquals(grass,world.clip(new ClipContext(eye,point,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty())).getBlockPos());
        assertTrue(TraversalGeometry.visible(world,null,eye,point,target));
        world.put(grass,Blocks.STONE.defaultBlockState());assertFalse(TraversalGeometry.visible(world,null,eye,point,target));
    }
    @ParameterizedTest @ValueSource(strings={"stone","oak_leaves","glass","oak_fence","cobblestone_wall","bamboo"})
    void solidObstaclesStillBlockBody(String id){
        var world=new Fixture();var feet=new BlockPos(0,1,0);world.put(feet,BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).defaultBlockState());
        assertTrue(TraversalGeometry.blocksMovement(world,feet));assertFalse(TraversalGeometry.clearBody(world,null,feet,1));
    }
    @ParameterizedTest @ValueSource(strings={"fire","soul_fire","sweet_berry_bush","wither_rose","lava","cactus","powder_snow"})
    void hazardsRemainForbiddenEvenWhenNotSolid(String id){
        var world=new Fixture();var feet=new BlockPos(0,1,0);world.put(feet,BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).defaultBlockState());
        assertFalse(TraversalGeometry.clearBody(world,null,feet,1));
    }
    @Test void upperSolidHalfStillBlocksHeadroom(){
        var world=new Fixture();var feet=new BlockPos(0,1,0);world.put(feet,Blocks.SHORT_GRASS.defaultBlockState());world.put(feet.above(),Blocks.STONE.defaultBlockState());
        assertFalse(TraversalGeometry.clearBody(world,null,feet,1));
    }
    @Test void transientEntityDoesNotRemoveAValidNarrowBridgeRoute(){
        var world=new Fixture();var feet=new BlockPos(0,1,0);
        world.put(feet.below(),Blocks.STONE.defaultBlockState());
        world.entityCollisions=List.of(net.minecraft.world.phys.shapes.Shapes.create(new AABB(0.2,1.01,0.2,0.8,2.8,0.8)));
        var body=new AABB(0.2,1.01,0.2,0.8,2.8,0.8);
        assertFalse(world.noCollision(null,body),"fixture must reproduce a companion occupying the bridge cell");
        assertTrue(TraversalGeometry.clearBody(world,null,feet,1),"block route remains traversable despite a temporary entity");
    }
}
