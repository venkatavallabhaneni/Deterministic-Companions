package dev.companions.inventory;

import net.minecraft.world.item.*;
import java.util.List;
import java.util.function.Consumer;

/** Spawn/duty equipment explicitly requested by the user; never overwrites existing contents silently. */
public final class IssuedSword {
    public static int ensure(List<ItemStack> slots,int selected,Consumer<ItemStack> overflow){
        for(int i=0;i<slots.size();i++)if(slots.get(i).is(Items.DIAMOND_SWORD))return i;
        int slot=-1;for(int i=0;i<slots.size();i++)if(slots.get(i).isEmpty()){slot=i;break;}
        if(slot<0){slot=selected;overflow.accept(slots.get(slot));}
        slots.set(slot,new ItemStack(Items.DIAMOND_SWORD));return slot;
    }
}
