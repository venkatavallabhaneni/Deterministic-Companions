package dev.companions.inventory;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Stow without deleting, duplicating or dropping equipment. Full inventories keep their stack. */
public final class EquipmentSelection {
    public static int stow(List<ItemStack> inventory,int selected){
        if(inventory.get(selected).isEmpty())return selected;
        for(int i=0;i<Math.min(9,inventory.size());i++)if(inventory.get(i).isEmpty())return i;
        for(int i=9;i<inventory.size();i++)if(inventory.get(i).isEmpty()){
            inventory.set(i,inventory.get(selected));inventory.set(selected,ItemStack.EMPTY);return selected;
        }
        return selected;
    }
}
