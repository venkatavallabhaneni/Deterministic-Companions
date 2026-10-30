package dev.companions.task;

import dev.companions.player.CompanionController;
import dev.companions.combat.CombatController;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class GuardTask extends BaseTask {
    private final Vec3 center;
    private final int radius;
    private int ticks;
    private LivingEntity threat;
    public GuardTask(CompanionController c,int radius) {super(c,"GUARD radius "+radius);center=c.player.position();this.radius=radius;}
    @Override public void start(){
        c.combat.passive=false;c.combat.rangedDuty=c.rangedDutyOverride!=null?c.rangedDutyOverride:CombatController.rangedAssignment(c.player.getPlainTextName());c.rangedDutyOverride=null;
        int index=c.formationIndexOverride==null?0:c.formationIndexOverride,count=c.formationCountOverride==null?1:c.formationCountOverride;
        c.formationIndexOverride=c.formationCountOverride=null;c.combat.formation(index,count);
        c.inventory.swordDuty=true;c.inventory.diamondSword();c.inventory.rangedSupplies();
    }
    @Override public void tick() {
        if(++ticks%10==1)threat=c.combat.threat(c.player,center,radius);
        if(threat!=null && threat.position().distanceToSqr(center)<=radius*radius && c.player.position().distanceToSqr(center)<(radius+3)*(radius+3) && c.combat.engage(threat))return;
        c.player.stopUsingItem();
        if(c.player.position().distanceToSqr(center)>4)c.navigation.target(center,1);else c.navigation.stop();
    }
}
