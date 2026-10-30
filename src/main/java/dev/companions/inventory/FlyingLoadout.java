package dev.companions.inventory;

import dev.companions.player.CompanionController;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import java.util.List;

/** The single operator-issued flyer loadout; real stacks and vanilla use. */
public final class FlyingLoadout {
    private final CompanionController c;
    public FlyingLoadout(CompanionController c){this.c=c;}
    public void maintain(){
        var p=c.player;
        ItemStack chest=p.getItemBySlot(EquipmentSlot.CHEST);
        if(!chest.is(Items.ELYTRA)){
            int slot=c.inventory.find("minecraft:elytra");
            ItemStack wings=slot>=0?p.getInventory().getItem(slot):new ItemStack(Items.ELYTRA);
            wings.set(DataComponents.UNBREAKABLE,Unit.INSTANCE);
            wings.setDamageValue(0);
            p.setItemSlot(EquipmentSlot.CHEST,wings);
            if(slot>=0)p.getInventory().setItem(slot,chest);
            else if(!chest.isEmpty()&&!c.inventory.grant(List.of(chest)))p.drop(chest,false,false);
        }else {chest.set(DataComponents.UNBREAKABLE,Unit.INSTANCE);chest.setDamageValue(0);}

        if(c.inventory.find("minecraft:mace")<0){
            ItemStack mace=new ItemStack(Items.MACE);
            mace.enchant(p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.WIND_BURST),2);
            if(!c.inventory.grant(List.of(mace)))replaceLastSlot(mace);
        }else{
            int slot=c.inventory.find("minecraft:mace");
            p.getInventory().getItem(slot).enchant(p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.WIND_BURST),2);
        }
        int rocket=c.inventory.find("minecraft:firework_rocket");
        if(rocket<0){
            ItemStack stack=new ItemStack(Items.FIREWORK_ROCKET,64);
            if(!c.inventory.grant(List.of(stack)))replaceLastSlot(stack);
        }else{
            // At most one stack is refilled, after normal server item use.
            p.getInventory().getItem(rocket).setCount(64);
        }
        p.inventoryMenu.broadcastChanges();
    }
    private void replaceLastSlot(ItemStack issued){
        var p=c.player;
        int slot=35;
        if(p.getInventory().getItem(slot).is(Items.MACE))slot=34;
        ItemStack displaced=p.getInventory().getItem(slot);
        p.getInventory().setItem(slot,issued);
        if(!displaced.isEmpty())p.drop(displaced,false,false);
    }
    public void holdMace(){int slot=c.inventory.find("minecraft:mace");if(slot>=0)c.inventory.select(slot);}
    public boolean firework(){
        int slot=c.inventory.find("minecraft:firework_rocket");
        if(slot<0||!c.player.isFallFlying())return false;
        c.inventory.select(slot);
        int before=c.player.getMainHandItem().getCount();
        c.player.gameMode.useItem(c.player,c.player.level(),c.player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
        boolean used=c.player.getMainHandItem().getCount()<before;
        holdMace();
        return used;
    }
}
