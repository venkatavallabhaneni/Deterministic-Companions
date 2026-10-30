package dev.companions.schematic;

import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** How each schematic block state is supplied, ordered and verified. */
public final class PlacementRules {
    private PlacementRules(){}
    /** Properties chosen at placement time. Everything else (fence/pane connections, stair shape,
     * open, powered, lit, chest pairing ...) is derived from neighbours or use and is not compared. */
    private static final Set<String> PLACED=Set.of("facing","axis","half","hinge","part","rotation","face","attachment","hanging","orientation");

    public static final int STRUCTURE=0,FITTINGS=1,FURNISHINGS=2;
    /** Build phase of a state: structure first, then fittings, then furnishings. */
    public static int stage(BlockState s){return furnishing(s)?FURNISHINGS:fitting(s)?FITTINGS:STRUCTURE;}
    /**
     * Containers and workstations go in last, after the building is complete and
     * every fitting is in place: they are interior furniture, never support
     * anything, and would otherwise crowd the floor builders stand on.
     */
    public static boolean furnishing(BlockState s){
        Block b=s.getBlock();
        return b instanceof AbstractChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock || b instanceof CraftingTableBlock
            || b instanceof CrafterBlock || b instanceof AbstractFurnaceBlock || b instanceof AnvilBlock || b instanceof EnchantingTableBlock
            || b instanceof BrewingStandBlock || b instanceof StonecutterBlock || b instanceof GrindstoneBlock || b instanceof LoomBlock
            || b instanceof LecternBlock || b instanceof ComposterBlock || b instanceof CartographyTableBlock || b instanceof JukeboxBlock;
    }
    /**
     * Attachments and fittings are placed after all structure: they need their
     * supporting wall/floor, ladders would otherwise be climbed over while still
     * incomplete, and open door holes keep routes in and out of the building.
     */
    public static boolean fitting(BlockState s){
        Block b=s.getBlock();
        return s.is(BlockTags.CLIMBABLE) || b instanceof LadderBlock || b instanceof TorchBlock || b instanceof LanternBlock
            || b instanceof SignBlock || b instanceof AbstractBannerBlock || b instanceof ButtonBlock || b instanceof LeverBlock
            || b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof BedBlock || b instanceof CarpetBlock
            || b instanceof BasePressurePlateBlock || b instanceof BaseRailBlock || b instanceof RedStoneWireBlock
            || b instanceof FlowerPotBlock || b instanceof CandleBlock || b instanceof BellBlock || b instanceof TripWireHookBlock
            || b instanceof VegetationBlock || b instanceof AbstractSkullBlock || b instanceof ChainBlock
            || b instanceof FaceAttachedHorizontalDirectionalBlock;
    }
    /** Upper door halves, bed heads and other second parts appear automatically when the first part is placed. */
    public static boolean secondPart(BlockState s){
        if(s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER)return true;
        return s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==BedPart.HEAD;
    }
    /** The second cell created with this state, or null. */
    public static Direction partnerDirection(BlockState s){
        if(s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))return s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN;
        if(s.getBlock() instanceof BedBlock){Direction f=s.getValue(BedBlock.FACING);return s.getValue(BedBlock.PART)==BedPart.FOOT?f:f.getOpposite();}
        return null;
    }
    public static BlockState partnerState(BlockState s){
        if(s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
            return s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER);
        if(s.getBlock() instanceof BedBlock)return s.setValue(BedBlock.PART,s.getValue(BedBlock.PART)==BedPart.FOOT?BedPart.HEAD:BedPart.FOOT);
        return null;
    }
    public static Item item(BlockState s){return s.getBlock().asItem();}
    public static int itemCount(BlockState s){
        if(secondPart(s))return 0;
        return s.getBlock() instanceof SlabBlock&&s.getValue(SlabBlock.TYPE)==SlabType.DOUBLE?2:1;
    }
    /** Gravity, fluids and item-less states cannot be built by placing an inventory item. */
    public static String unsupported(BlockState s){
        if(!s.getFluidState().isEmpty()&&s.getBlock() instanceof LiquidBlock)return "fluid";
        if(s.getBlock() instanceof BaseFireBlock)return "fire";
        if(item(s)==Items.AIR||!(item(s) instanceof BlockItem))return "no placeable item";
        return null;
    }
    /** Slab type (bottom/top/double) and chest type (single/left/right) are part of what was built. */
    private static boolean compared(Property<?> p,BlockState target){
        return PLACED.contains(p.getName())||p.getName().equals("type")&&(target.getBlock() instanceof SlabBlock||target.getBlock() instanceof ChestBlock);
    }
    public static boolean matches(BlockState actual,BlockState target){
        if(actual.getBlock()!=target.getBlock())return false;
        for(Property<?> p:target.getProperties()){
            boolean compared=compared(p,target);
            if(compared&&actual.hasProperty(p)&&!actual.getValue(p).equals(target.getValue(p)))return false;
        }
        return true;
    }
    /** Copies the placement-chosen properties of target onto actual (same block). */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static BlockState adjust(BlockState actual,BlockState target){
        if(actual.getBlock()!=target.getBlock())return target;
        BlockState out=actual;
        for(Property p:target.getProperties()){
            boolean compared=compared(p,target);
            if(compared&&out.hasProperty(p))out=out.setValue(p,target.getValue(p));
        }
        return out;
    }
    /** Direction from the target cell to the block it should be clicked against, best first. */
    public static List<Direction> supportOrder(BlockState s){
        var out=new ArrayList<Direction>(6);
        if(s.hasProperty(BlockStateProperties.ATTACH_FACE)){
            var face=s.getValue(BlockStateProperties.ATTACH_FACE);
            out.add(face==AttachFace.FLOOR?Direction.DOWN:face==AttachFace.CEILING?Direction.UP:s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        }else if(s.hasProperty(BlockStateProperties.HANGING)&&s.getValue(BlockStateProperties.HANGING))out.add(Direction.UP);
        else if(wallAttached(s)&&s.hasProperty(BlockStateProperties.HORIZONTAL_FACING))out.add(s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        else if(s.hasProperty(BlockStateProperties.HALF)&&s.getValue(BlockStateProperties.HALF)==Half.TOP)out.add(Direction.UP);
        else if(s.getBlock() instanceof SlabBlock&&s.getValue(SlabBlock.TYPE)==SlabType.TOP)out.add(Direction.UP);
        for(Direction d:new Direction[]{Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP})if(!out.contains(d))out.add(d);
        return out;
    }
    public static boolean wallAttached(BlockState s){
        Block b=s.getBlock();
        return b instanceof LadderBlock||b instanceof WallTorchBlock||b instanceof WallSignBlock||b instanceof WallHangingSignBlock||b instanceof WallBannerBlock||b instanceof WallSkullBlock||b instanceof TripWireHookBlock;
    }
    /** Doors, beds, stairs and similar take their facing from the placer's yaw. */
    public static Direction placerFacing(BlockState s){
        if(!s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)||wallAttached(s))return null;
        Direction f=s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Block b=s.getBlock();
        // These blocks face back toward the player who places them.
        if(b instanceof ChestBlock||b instanceof AbstractFurnaceBlock||b instanceof BarrelBlock||b instanceof EnderChestBlock)return f.getOpposite();
        return f;
    }
}
