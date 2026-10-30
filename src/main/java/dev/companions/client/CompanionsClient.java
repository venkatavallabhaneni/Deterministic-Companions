package dev.companions.client;

import dev.companions.selection.FlyingTargetStick;
import dev.companions.selection.TargetMobPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;

/** Client key only selects an entity ID; the server authorizes the actual order. */
public final class CompanionsClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        KeyMapping.Category category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("deterministic_companions","controls"));
        KeyMapping target=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.deterministic_companions.target",InputConstants.Type.KEYSYM,InputConstants.KEY_R,category));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(target.consumeClick())target(client);
        });
    }
    private static void target(Minecraft client){
        if(client.player==null||FlyingTargetStick.target(client.player.getMainHandItem())==null)return;
        var hit=ProjectileUtil.getHitResultOnViewVector(client.player,
            e->e instanceof Mob,30);
        if(hit instanceof EntityHitResult entityHit)ClientPlayNetworking.send(new TargetMobPayload(entityHit.getEntity().getId()));
        else client.player.sendSystemMessage(Component.literal("Aim at a mob within 30 blocks"));
    }
}
