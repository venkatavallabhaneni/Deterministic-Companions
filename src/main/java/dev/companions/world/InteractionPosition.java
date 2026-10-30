package dev.companions.world;

import dev.companions.player.CompanionController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;
import static dev.companions.navigation.WorldNeighbors.*;

public final class InteractionPosition {
    public static boolean approach(CompanionController c,BlockPos block) {
        return approach(c,block,false);
    }
    public static boolean approach(CompanionController c,BlockPos block,boolean mining) {
        BlockPos chosen=find(c,block,mining,p->true);
        if(chosen==null)return false;
        if(mining&&!c.workFrom(chosen))return false;
        c.navigation.target(Vec3.atBottomCenterOf(chosen),0.7);return true;
    }
    public static BlockPos find(CompanionController c,BlockPos block,boolean mining,Predicate<BlockPos> allowed) {
        double best=Double.POSITIVE_INFINITY; Vec3 chosen=null;
        for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) for(int dy=-2;dy<=1;dy++) {
            if(dx==0&&dz==0) continue;
            BlockPos p=block.offset(dx,dy,dz);
            if(!allowed.test(p))continue;
            boolean supported=c.navigation.world.stand(cell(p));
            if(!supported&&!(c.config.bridge&&c.config.placeBlocks&&c.inventory.buildingCount()>0
                &&c.navigation.world.clear(cell(p))&&c.navigation.world.safe(cell(p.below()))&&c.navigation.world.replaceable(cell(p.below()))))continue;
            if(mining&&!c.canWorkFrom(p))continue;
            Vec3 foot=Vec3.atBottomCenterOf(p),eye=foot.add(0,c.player.getEyeHeight(),0);
            if(eye.distanceToSqr(Vec3.atCenterOf(block))>16) continue;
            if(!dev.companions.navigation.TraversalGeometry.visible(c.player.level(),c.player,eye,Vec3.atCenterOf(block),block))continue;
            double score=foot.distanceToSqr(c.player.position())+Math.abs(dy)*2+(supported?0:16);
            if(score<best) {best=score;chosen=foot;}
        }
        return chosen==null?null:BlockPos.containing(chosen);
    }
}
