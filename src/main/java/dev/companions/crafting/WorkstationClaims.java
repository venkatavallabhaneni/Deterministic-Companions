package dev.companions.crafting;

import net.minecraft.core.BlockPos;
import java.util.*;

/** One private table site per active companion; survives task replacement. */
public final class WorkstationClaims {
    private record Site(String dimension,BlockPos pos){}
    private final Map<UUID,Site> sites=new HashMap<>();
    public boolean available(UUID owner,String dimension,BlockPos pos){
        return sites.entrySet().stream().noneMatch(e->!e.getKey().equals(owner)&&e.getValue().dimension.equals(dimension)&&e.getValue().pos.distSqr(pos)<25);
    }
    public boolean reserve(UUID owner,String dimension,BlockPos pos){
        if(!available(owner,dimension,pos))return false;sites.put(owner,new Site(dimension,pos.immutable()));return true;
    }
    public void release(UUID owner){sites.remove(owner);}
}
