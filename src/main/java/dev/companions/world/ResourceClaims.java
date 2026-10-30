package dev.companions.world;

import java.util.*;

/** One expiring mining reservation per active bot. Accessed only on the server thread. */
public final class ResourceClaims {
    public final dev.companions.crafting.WorkstationClaims stations=new dev.companions.crafting.WorkstationClaims();
    public static final int WORK_SPACING=5;
    private record Claim(String dimension,long position,long expires,long workplace) {}
    private record Breaking(String dimension,long position,long expires) {}
    private final Map<UUID,Claim> claims=new HashMap<>();
    private final Map<UUID,Breaking> breaking=new HashMap<>();
    public boolean available(UUID bot,String dimension,long position,long tick){
        claims.values().removeIf(c->c.expires<=tick);
        breaking.values().removeIf(c->c.expires<=tick);
        return claims.entrySet().stream().noneMatch(e->!e.getKey().equals(bot)&&e.getValue().dimension.equals(dimension)&&e.getValue().position==position)
            &&breaking.entrySet().stream().noneMatch(e->!e.getKey().equals(bot)&&e.getValue().dimension.equals(dimension)&&e.getValue().position==position);
    }
    public boolean claim(UUID bot,String dimension,long position,long tick){
        if(!available(bot,dimension,position,tick))return false;
        var old=claims.get(bot);
        claims.put(bot,new Claim(dimension,position,tick+200,old!=null&&old.position==position?old.workplace:position));return true;
    }
    private static boolean near(long a,long b){return net.minecraft.core.BlockPos.of(a).distSqr(net.minecraft.core.BlockPos.of(b))<WORK_SPACING*WORK_SPACING;}
    public boolean workAvailable(UUID bot,String dimension,long position,long tick){
        if(!available(bot,dimension,position,tick))return false;
        return claims.entrySet().stream().noneMatch(e->!e.getKey().equals(bot)&&e.getValue().dimension.equals(dimension)&&near(e.getValue().position,position));
    }
    public boolean claimWork(UUID bot,String dimension,long position,long tick){
        return workAvailable(bot,dimension,position,tick)&&claim(bot,dimension,position,tick);
    }
    public boolean canWorkFrom(UUID bot,String dimension,long position,long tick){
        available(bot,dimension,position,tick);
        return claims.entrySet().stream().noneMatch(e->!e.getKey().equals(bot)&&e.getValue().dimension.equals(dimension)&&near(e.getValue().workplace,position));
    }
    public boolean workFrom(UUID bot,String dimension,long position,long tick){
        if(!canWorkFrom(bot,dimension,position,tick))return false;
        var old=claims.get(bot);if(old==null)return false;
        claims.put(bot,new Claim(old.dimension,old.position,tick+200,position));return true;
    }
    public boolean startBreaking(UUID bot,String dimension,long position,long tick){
        breaking.values().removeIf(b->b.expires<=tick);
        if(!available(bot,dimension,position,tick)||breaking.entrySet().stream().anyMatch(e->!e.getKey().equals(bot)&&e.getValue().dimension.equals(dimension)&&e.getValue().position==position))return false;
        breaking.put(bot,new Breaking(dimension,position,tick+40));return true;
    }
    public void stopBreaking(UUID bot){breaking.remove(bot);}
    public boolean hasOtherWorkers(UUID bot,String dimension,long tick){
        claims.values().removeIf(c->c.expires<=tick);
        return claims.entrySet().stream().anyMatch(e->!e.getKey().equals(bot)&&e.getValue().dimension.equals(dimension));
    }
    public void release(UUID bot){claims.remove(bot);stopBreaking(bot);}
}
