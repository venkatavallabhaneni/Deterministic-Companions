package dev.companions.building;

import net.minecraft.core.BlockPos;
import java.util.function.*;

/**
 * Per-companion overrides used while a schematic is being built. Navigation
 * places construction blocks through {@code InventoryController.buildingSlot()},
 * so routing every pillar/bridge block through one dedicated scaffold item lets
 * the build record each temporary block and remove it afterwards. Finished
 * schematic blocks are protected from navigation digging.
 */
public final class ConstructionPolicy {
    public Consumer<BlockPos> placed=p->{};
    public Predicate<BlockPos> protectedBlock=p->false;
    /** Extra route cost for entering (or placing into) a cell, e.g. where another companion works. */
    public ToDoubleFunction<dev.companions.navigation.path.Cell> crowdCost=p->0;
    /** Extra route cost for a pillar/bridge block placed into a cell, e.g. one the build must fill soon. */
    public ToDoubleFunction<dev.companions.navigation.path.Cell> placementCost=p->0;
    /** Extra route cost for breaking a block on the way (finished schematic blocks are a last resort). */
    public ToDoubleFunction<dev.companions.navigation.path.Cell> breakCost=p->0;
    /** Told about every block the companion breaks. */
    public Consumer<BlockPos> broken=p->{};
    /** Longest drop navigation will take. Raised briefly for a companion stranded on a structure. */
    public static final int SAFE_FALL=3;
    public int maxFall=SAFE_FALL;
    public void reset(){placed=p->{};protectedBlock=p->false;crowdCost=p->0;placementCost=p->0;breakCost=p->0;broken=p->{};maxFall=SAFE_FALL;}
}
