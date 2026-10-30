package dev.companions.crafting;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Maximum matching reserves existing inputs before calculating one recipe batch's deficit. */
public final class RecipeMaterials {
    public static boolean roomAfterConsumption(List<ItemStack> inventory,int[] allocatedSlots,ItemStack result){
        var copy=inventory.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for(int slot:allocatedSlots){if(copy.get(slot).isEmpty())return false;copy.get(slot).shrink(1);}
        return dev.companions.inventory.MaterialGrants.insert(copy,List.of(result));
    }
    public static List<ItemStack> missing(List<Ingredient> ingredients,List<ItemStack> inventory) {
        List<ItemStack> units=new ArrayList<>();
        for(var stack:inventory)for(int n=0;n<Math.min(ingredients.size(),stack.getCount());n++)units.add(stack);
        int[] owners=new int[units.size()];Arrays.fill(owners,-1);
        boolean[] matched=new boolean[ingredients.size()];
        for(int i=0;i<ingredients.size();i++)matched[i]=assign(i,ingredients,units,owners,new boolean[units.size()]);
        List<ItemStack> missing=new ArrayList<>();
        for(int i=0;i<ingredients.size();i++)if(!matched[i]){
            var ingredient=ingredients.get(i);
            var item=ingredient.items().map(h->h.value()).filter(v->ingredient.test(new ItemStack(v)))
                .min(Comparator.comparing(v->BuiltInRegistries.ITEM.getKey(v).toString()));
            if(item.isEmpty())return null;
            missing.add(new ItemStack(item.get()));
        }
        return missing;
    }
    private static boolean assign(int i,List<Ingredient> ingredients,List<ItemStack> units,int[] owners,boolean[] seen){
        for(int u=0;u<units.size();u++)if(!seen[u]&&ingredients.get(i).test(units.get(u))){
            seen[u]=true;
            if(owners[u]<0||assign(owners[u],ingredients,units,owners,seen)){owners[u]=i;return true;}
        }
        return false;
    }
}
