package dev.companions.task;

import dev.companions.player.CompanionController;
import net.minecraft.world.phys.Vec3;
import static dev.companions.navigation.WorldNeighbors.*;

public final class EscapeTask extends BaseTask {
    private boolean chosen;
    public EscapeTask(CompanionController c) {super(c,"ESCAPE");}
    @Override public void tick() {
        if(chosen) {if(c.navigation.arrived()) finish("ESCAPE complete"); else routeFailed();return;}
        var start=cell(c.player.blockPosition()); Vec3 threat=c.player.getLastHurtByMob()==null?c.player.position():c.player.getLastHurtByMob().position();
        double best=-Double.MAX_VALUE; dev.companions.navigation.path.Cell target=null;
        for(int dx=-8;dx<=8;dx+=2) for(int dz=-8;dz<=8;dz+=2) for(int dy=-2;dy<=3;dy++) {
            var p=start.add(dx,dy,dz); if(!c.navigation.world.stand(p) || c.navigation.world.water(p)) continue;
            Vec3 v=Vec3.atBottomCenterOf(pos(p)); double score=v.distanceToSqr(threat)-v.distanceToSqr(c.player.position())*0.25;
            if(score>best) {best=score;target=p;}
        }
        if(target==null) {finish("ESCAPE: no safe loaded destination");return;}
        c.navigation.target(Vec3.atBottomCenterOf(pos(target)),1);chosen=true;
    }
}
