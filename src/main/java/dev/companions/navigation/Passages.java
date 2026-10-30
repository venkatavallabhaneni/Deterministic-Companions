package dev.companions.navigation;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Doors and fence gates. A closed door is only a thin panel along one edge of
 * its cell, so the body-clearance test alone sees the cell as walkable and a
 * companion would push against the panel forever; routes must open it instead.
 */
public final class Passages {
    private Passages(){}
    public static boolean passage(BlockState s){return s.getBlock() instanceof DoorBlock||s.getBlock() instanceof FenceGateBlock;}
    /** Iron doors and gates need redstone and are treated as walls. */
    public static boolean openable(BlockState s){
        if(s.getBlock() instanceof DoorBlock door)return door.type().canOpenByHand();
        return s.getBlock() instanceof FenceGateBlock;
    }
    public static boolean open(BlockState s){return s.hasProperty(BlockStateProperties.OPEN)&&s.getValue(BlockStateProperties.OPEN);}
}
