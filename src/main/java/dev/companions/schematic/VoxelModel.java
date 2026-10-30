package dev.companions.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * A snapshot of the build site as whole blocks, used to plan a build off the
 * server thread. Cells are solid (something with a collision shape) or not;
 * hazards (lava, fire ...) are never stood on or pillared through. Outside the
 * snapshot box everything counts as unknown, so plans stay inside it.
 */
public final class VoxelModel implements StandFinder.View {
    private final BlockPos min,max;
    private final Set<Long> solid=new HashSet<>(),hazard=new HashSet<>();
    public VoxelModel(BlockPos min,BlockPos max){this.min=min.immutable();this.max=max.immutable();}
    public BlockPos min(){return min;}
    public BlockPos max(){return max;}
    public void setSolid(BlockPos p,boolean value){if(value)solid.add(p.asLong());else solid.remove(p.asLong());}
    public void setHazard(BlockPos p){hazard.add(p.asLong());}
    public boolean inside(BlockPos p){
        return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();
    }
    @Override public boolean loaded(BlockPos p){return inside(p);}
    @Override public boolean hazard(BlockPos p){return !inside(p)||hazard.contains(p.asLong());}
    @Override public boolean solid(BlockPos p){return solid.contains(p.asLong());}
    @Override public boolean replaceable(BlockPos p){return inside(p)&&!solid(p);}
    @Override public boolean clear(BlockPos feet){return inside(feet)&&inside(feet.above())&&!solid(feet)&&!solid(feet.above())&&!hazard(feet)&&!hazard(feet.above());}
    @Override public boolean stand(BlockPos feet){return clear(feet)&&solid(feet.below());}
    /**
     * Voxel traversal (Amanatides–Woo) from an eye point to an aim point. The
     * first solid cell crossed must be target itself; reaching the aim point
     * through air counts as visible.
     */
    @Override public boolean visible(Vec3 from,Vec3 to,BlockPos target){
        double dx=to.x-from.x,dy=to.y-from.y,dz=to.z-from.z;
        int x=(int)Math.floor(from.x),y=(int)Math.floor(from.y),z=(int)Math.floor(from.z);
        int ex=(int)Math.floor(to.x),ey=(int)Math.floor(to.y),ez=(int)Math.floor(to.z);
        int sx=dx>0?1:dx<0?-1:0,sy=dy>0?1:dy<0?-1:0,sz=dz>0?1:dz<0?-1:0;
        double tdx=sx==0?Double.POSITIVE_INFINITY:Math.abs(1/dx),tdy=sy==0?Double.POSITIVE_INFINITY:Math.abs(1/dy),tdz=sz==0?Double.POSITIVE_INFINITY:Math.abs(1/dz);
        double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?x+1-from.x:from.x-x)*tdx);
        double ty=sy==0?Double.POSITIVE_INFINITY:((sy>0?y+1-from.y:from.y-y)*tdy);
        double tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?z+1-from.z:from.z-z)*tdz);
        for(int guard=0;guard<256;guard++){
            if(guard>0){
                var here=new BlockPos(x,y,z);
                if(solid(here))return here.equals(target);
            }
            if(x==ex&&y==ey&&z==ez)return true;
            double next=Math.min(tx,Math.min(ty,tz));
            if(next>1)return true;
            if(tx<=ty&&tx<=tz){x+=sx;tx+=tdx;}
            else if(ty<=tz){y+=sy;ty+=tdy;}
            else{z+=sz;tz+=tdz;}
        }
        return true;
    }
}
