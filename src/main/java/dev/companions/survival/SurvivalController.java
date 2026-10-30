package dev.companions.survival;

import dev.companions.player.CompanionController;
import dev.companions.task.EscapeTask;

/** Called before the primary task, returning exclusive control for the current tick. */
public final class SurvivalController {
    private final CompanionController c;
    private EscapeTask escape;
    private int ticks;
    private net.minecraft.world.phys.Vec3 emergencyDestination;
    public SurvivalController(CompanionController c){this.c=c;}
    public boolean tick() {
        ticks++;
        if(ticks%40==0&&!c.flyer)c.inventory.armor();
        if(c.player.isInWater() && c.player.getAirSupply()<100) {c.actions.cancelBreak();c.navigation.pause();c.actions.jump();return true;}
        if(c.player.isInLava()) {
            if(emergencyDestination==null || ticks%20==0) emergencyDestination=findEmergencyDestination(false);
            c.navigation.pause();c.actions.cancelBreak();
            if(emergencyDestination!=null)c.actions.move(emergencyDestination,false);
            c.actions.jump();c.player.setDeltaMovement(c.player.getDeltaMovement().add(0,0.04,0));return true;
        }
        if(c.player.isOnFire() && !c.player.isInWater()) {
            if(emergencyDestination==null || ticks%20==0)emergencyDestination=findEmergencyDestination(true);
            if(emergencyDestination!=null){c.actions.cancelBreak();c.navigation.target(emergencyDestination,0.5);return true;}
        } else emergencyDestination=null;
        if(SurvivalPolicy.shouldRetreat(c.player.getHealth(),c.player.getLastHurtByMob()!=null,c.player.tickCount,c.player.getLastHurtByMobTimestamp())) {
            if(escape==null || escape.isComplete()){c.navigation.stop();escape=new EscapeTask(c);}
            escape.tick();
            return true;
        }
        if(escape!=null){escape.cancel();escape=null;}
        if(c.player.isUsingItem() && c.player.getUseItem().has(net.minecraft.core.component.DataComponents.FOOD)) {c.navigation.pause();c.actions.cancelBreak();return true;}
        if(c.player.getFoodData().getFoodLevel()<=16 && ticks%10==0 && c.inventory.eat()){c.navigation.pause();c.actions.cancelBreak();return true;}
        if(!c.combat.passive && !c.swordDuty()) {
            var attacker=c.player.getLastHurtByMob();
            if(attacker!=null && c.player.tickCount-c.player.getLastHurtByMobTimestamp()<100 && c.player.distanceToSqr(attacker)<36 && c.combat.allowed(attacker)) {
                c.actions.cancelBreak();return c.combat.engage(attacker);
            }
        }
        return false;
    }
    private net.minecraft.world.phys.Vec3 findEmergencyDestination(boolean water) {
        var start=dev.companions.navigation.WorldNeighbors.cell(c.player.blockPosition());
        net.minecraft.world.phys.Vec3 chosen=null;double best=Double.POSITIVE_INFINITY;
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-1;y<=3;y++) {
            var cell=start.add(x,y,z);if(!c.navigation.world.stand(cell) || water!=c.navigation.world.water(cell))continue;
            var point=net.minecraft.world.phys.Vec3.atBottomCenterOf(dev.companions.navigation.WorldNeighbors.pos(cell));double distance=c.player.position().distanceToSqr(point);
            if(distance<best){best=distance;chosen=point;}
        }
        return chosen;
    }
}
