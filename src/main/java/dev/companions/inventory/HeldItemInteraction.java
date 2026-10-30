package dev.companions.inventory;

import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.*;

/** Temporarily empty a hand without requiring an empty inventory slot. */
public final class HeldItemInteraction {
    public static <T> T emptyHand(List<ItemStack> inventory,int selected,Supplier<T> action,Consumer<ItemStack> received){
        var held=inventory.get(selected);inventory.set(selected,ItemStack.EMPTY);
        try{return action.get();}
        finally{
            var extra=inventory.get(selected);inventory.set(selected,held);
            if(!extra.isEmpty())received.accept(extra);
        }
    }
}
