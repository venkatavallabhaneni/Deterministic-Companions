package dev.companions.task;

import dev.companions.player.CompanionController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import static dev.companions.navigation.WorldNeighbors.*;

public final class ExploreTask extends BaseTask {
    private final BlockPos center;
    private final int radius;
    private int waypoint;
    private boolean traveling;
    public ExploreTask(CompanionController c,int radius) {super(c,"EXPLORE");this.radius=radius;center=c.player.blockPosition();}
    @Override public void tick() {
        if(traveling && !c.navigation.arrived() && !c.navigation.failed()) return;
        if(waypoint>=32) {finish("EXPLORE complete");return;}
        // Fixed angular sequence and increasing rings: no random state.
        double a=(waypoint%8)*Math.PI/4; int r=Math.max(2,radius*(waypoint/8+1)/4);waypoint++;
        BlockPos p=center.offset((int)Math.round(Math.cos(a)*r),0,(int)Math.round(Math.sin(a)*r));
        for(int dy=8;dy>=-8;dy--) if(c.navigation.world.stand(cell(p.above(dy)))) {c.navigation.stop();c.navigation.target(Vec3.atBottomCenterOf(p.above(dy)),1);traveling=true;return;}
        c.navigation.stop();traveling=false;
    }
}
