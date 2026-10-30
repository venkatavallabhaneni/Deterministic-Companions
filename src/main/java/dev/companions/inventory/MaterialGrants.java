package dev.companions.inventory;

import net.minecraft.world.item.ItemStack;
import java.util.*;

/** All-or-nothing grants; never replace held items or spill repeated grants onto the ground. */
public final class MaterialGrants {
    public static boolean insert(List<ItemStack> inventory,List<ItemStack> incoming){
        var copy=inventory.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for(var original:incoming){
            var rest=original.copy();
            for(var stack:copy)if(!rest.isEmpty()&&ItemStack.isSameItemSameComponents(stack,rest)){
                int n=Math.min(rest.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount()));stack.grow(n);rest.shrink(n);
            }
            for(int i=0;i<copy.size()&&!rest.isEmpty();i++)if(copy.get(i).isEmpty()){
                int n=Math.min(rest.getCount(),rest.getMaxStackSize());copy.set(i,rest.copyWithCount(n));rest.shrink(n);
            }
            if(!rest.isEmpty())return false;
        }
        for(int i=0;i<copy.size();i++)inventory.set(i,copy.get(i));
        return true;
    }
}
