package dev.companions.task;

import dev.companions.player.CompanionController;
import net.minecraft.world.phys.Vec3;

public final class StayTask extends BaseTask {
    private final Vec3 anchor;
    public StayTask(CompanionController c) {super(c,"STAY");anchor=c.player.position();}
    @Override public void tick() {if(c.player.position().distanceToSqr(anchor)>1.5) c.navigation.target(anchor,0.7);else c.navigation.stop();}
}
