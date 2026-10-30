package dev.companions.inventory;

import net.minecraft.world.item.*;
import java.util.List;

public final class GuardSupplies {
    public static final int STARTER_DIRT=64;
    public static boolean issueOnce(List<ItemStack> inventory,boolean alreadyIssued){
        return alreadyIssued||MaterialGrants.insert(inventory,List.of(new ItemStack(Items.DIRT,STARTER_DIRT)));
    }
}
