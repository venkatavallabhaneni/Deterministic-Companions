package dev.companions.task;

import dev.companions.player.CompanionController;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

public final class AttackTask extends BaseTask {
    private final UUID target;
    private int ticks;
    public AttackTask(CompanionController c,LivingEntity target) {
        super(c,"ATTACK "+target.getName().getString());
        if(!c.combat.allowed(target))throw new IllegalArgumentException("Target protected by PVP/friendly-fire policy");
        this.target=target.getUUID();
    }
    @Override public void start() {c.combat.passive=false;}
    @Override public void tick() {
        var e=c.player.level().getEntity(target);
        if(!(e instanceof LivingEntity living)||!living.isAlive()){finish("ATTACK complete: target gone");return;}
        if(++ticks>2400 || routeFailed()){finish("ATTACK: timed out or unreachable");return;}
        if(!c.combat.engage(living))finish("ATTACK cancelled by combat policy");
    }
}
