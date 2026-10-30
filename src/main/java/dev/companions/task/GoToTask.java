package dev.companions.task;

import dev.companions.player.CompanionController;
import net.minecraft.world.phys.Vec3;

public class GoToTask extends BaseTask {
    protected final Vec3 destination;
    public GoToTask(CompanionController c,Vec3 destination) {super(c,"GOTO "+destination);this.destination=destination;}
    @Override public void start() {c.navigation.target(destination,0.7);}
    @Override public void tick() {if(routeFailed()) return; if(c.navigation.arrived()) finish("GOTO complete"); else c.navigation.target(destination,0.7);}
}
