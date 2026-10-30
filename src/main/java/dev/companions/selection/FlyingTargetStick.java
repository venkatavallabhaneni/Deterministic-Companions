package dev.companions.selection;

import dev.companions.player.CompanionController;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import java.util.UUID;

/** A named, owner-bound targeting tool; the server checks it again on every key press. */
public final class FlyingTargetStick {
    private static final String MARKER="companions_flying_target";
    private FlyingTargetStick(){}
    public static UUID target(ItemStack stack){
        if(!stack.is(Items.STICK))return null;
        String value=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr(MARKER,"");
        try{return UUID.fromString(value);}catch(IllegalArgumentException e){return null;}
    }
    public static ItemStack create(UUID companion){
        ItemStack stick=new ItemStack(Items.STICK);
        CompoundTag tag=new CompoundTag();tag.putString(MARKER,companion.toString());
        stick.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stick.set(DataComponents.CUSTOM_NAME,Component.literal("Flying Target Stick"));
        return stick;
    }
    public static void give(ServerPlayer player,CompanionController flyer){
        int slot=-1;
        for(int i=0;i<36;i++)if(flyer.player.getUUID().equals(target(player.getInventory().getItem(i)))){slot=i;break;}
        if(slot<0){
            for(int i=0;i<36;i++)if(player.getInventory().getItem(i).isEmpty()){slot=i;break;}
            if(slot<0){slot=player.getInventory().getSelectedSlot();player.drop(player.getInventory().getItem(slot).copy(),false,false);}
            player.getInventory().setItem(slot,create(flyer.player.getUUID()));
        }
        if(slot<9)player.getInventory().setSelectedSlot(slot);
        else{int held=player.getInventory().getSelectedSlot();ItemStack old=player.getInventory().getItem(held);player.getInventory().setItem(held,player.getInventory().getItem(slot));player.getInventory().setItem(slot,old);}
        player.inventoryMenu.broadcastChanges();
    }
}
