package dev.companions.task;

import dev.companions.player.CompanionController;
import net.minecraft.world.phys.Vec3;

public final class HomeTask extends GoToTask {
    public HomeTask(CompanionController c) {
        super(c,Vec3.atBottomCenterOf(c.home));
        if(!c.player.level().dimension().identifier().toString().equals(c.homeDimension)) throw new IllegalArgumentException("Home is in another dimension");
    }
    @Override public String getStatus() {return super.getStatus().replace("GOTO","HOME");}
}
