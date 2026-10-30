package dev.companions.crafting;

import dev.companions.player.CompanionController;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ToolRequirements {
    private ToolRequirements(){}
    public static String kind(BlockState state) {
        if(state.is(BlockTags.MINEABLE_WITH_PICKAXE))return "pickaxe";
        if(state.is(BlockTags.MINEABLE_WITH_AXE))return "axe";
        if(state.is(BlockTags.MINEABLE_WITH_SHOVEL))return "shovel";
        if(state.is(BlockTags.MINEABLE_WITH_HOE))return "hoe";
        return state.requiresCorrectToolForDrops()?"pickaxe":null;
    }
    public static Item cheapest(BlockState state) {
        String kind=kind(state);if(kind==null)return null;
        for(String material:new String[]{"wooden","stone","iron","diamond","netherite"}) {
            Item tool=BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:"+material+"_"+kind));
            if(!state.requiresCorrectToolForDrops() || new ItemStack(tool).isCorrectToolForDrops(state))return tool;
        }
        return null;
    }
    public static boolean equippedOrAvailable(CompanionController c,BlockState state) {
        String kind=kind(state);if(kind==null)return true;
        for(int i=0;i<36;i++) {
            var s=c.player.getInventory().getItem(i);
            if(s.isEmpty() || s.isDamageableItem()&&s.getMaxDamage()-s.getDamageValue()<=1)continue;
            if((!state.requiresCorrectToolForDrops()||s.isCorrectToolForDrops(state)) && s.getDestroySpeed(state)>1)return true;
        }
        return false;
    }
}
