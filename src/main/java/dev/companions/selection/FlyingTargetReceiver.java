package dev.companions.selection;

import dev.companions.CompanionsMod;
import dev.companions.task.FlyingHunterTask;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class FlyingTargetReceiver {
    private FlyingTargetReceiver(){}
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(TargetMobPayload.TYPE,TargetMobPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TargetMobPayload.TYPE,(payload,context)->{
            var sender=context.player();
            context.server().execute(()->{
                if(sender.isSpectator()||!sender.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))return;
                var id=FlyingTargetStick.target(sender.getMainHandItem());
                if(id==null)return;
                var flyer=CompanionsMod.manager(context.server()).all().stream()
                    .filter(c->c.flyer&&c.player.getUUID().equals(id)&&c.owner.equals(sender.getUUID())&&c.player.level()==sender.level()).findFirst().orElse(null);
                if(flyer==null)return;
                var entity=sender.level().getEntity(payload.entityId());
                if(!(entity instanceof Mob mob)||!flyer.combat.allowedExplicitMob(mob)
                    ||sender.distanceToSqr(mob)>900||!sender.hasLineOfSight(mob)){
                    sender.sendSystemMessage(Component.literal("Aim at a visible mob within 30 blocks"));return;
                }
                FlyingHunterTask task;
                if(flyer.tasks.current() instanceof FlyingHunterTask active)task=active;
                else{task=new FlyingHunterTask(flyer);flyer.tasks.set(task);}
                task.designate(mob);
                sender.sendSystemMessage(Component.literal(flyer.player.getPlainTextName()+" attacking "+mob.getName().getString()));
            });
        });
    }
}
