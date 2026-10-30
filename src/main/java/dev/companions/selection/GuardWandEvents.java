package dev.companions.selection;

import dev.companions.CompanionsMod;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionResult;

public final class GuardWandEvents {
    public static void register(){
        AttackBlockCallback.EVENT.register((player,level,hand,pos,face)->{
            if(!GuardWand.isWand(player.getItemInHand(hand)))return InteractionResult.PASS;
            if(player instanceof ServerPlayer p){
                if(!p.isSpectator()&&p.isWithinBlockInteractionRange(pos,0)&&p.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))CompanionsMod.manager(p.level().getServer()).wand.click(p,pos,true);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level,pos));
            }
            return InteractionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!GuardWand.isWand(player.getItemInHand(hand)))return InteractionResult.PASS;
            if(player instanceof ServerPlayer p&&!p.isSpectator()&&p.isWithinBlockInteractionRange(hit.getBlockPos(),0)&&p.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))CompanionsMod.manager(p.level().getServer()).wand.click(p,hit.getBlockPos(),false);
            return InteractionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,entity)->!GuardWand.isWand(player.getMainHandItem()));
    }
}
