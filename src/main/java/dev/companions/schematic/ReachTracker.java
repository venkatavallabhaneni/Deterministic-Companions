package dev.companions.schematic;

import java.util.*;

/**
 * Who can actually get to each layer. When a builder keeps failing to reach a
 * layer that another builder is placing blocks on, it is benched for that
 * layer: it stops claiming there and leaves the layer to the builders that
 * can reach it, instead of burning the layer's attempts and crowding them.
 *
 * A bench lifts as soon as no other active builder is reaching the layer, or
 * after BENCH_TICKS (new scaffolding may have opened a way up), and never
 * applies to a builder that has itself placed a block in the layer.
 */
public final class ReachTracker {
    public static final int FAILS_TO_BENCH=2;
    public static final long BENCH_TICKS=1200;
    private record Key(UUID bot,int stage,int y) {}
    private final Map<Key,Integer> fails=new HashMap<>(),wins=new HashMap<>();
    private final Map<Key,Long> benchedUntil=new HashMap<>();

    public void reached(UUID bot,int stage,int y){
        var k=new Key(bot,stage,y);
        wins.merge(k,1,Integer::sum);fails.remove(k);benchedUntil.remove(k);
    }
    /** A failed attempt that came from not getting to the block (no spot, no route, timed out, stuck). */
    public void missed(UUID bot,int stage,int y){fails.merge(new Key(bot,stage,y),1,Integer::sum);}
    public boolean benched(UUID bot,int stage,int y,long now,Collection<UUID> builders){
        var k=new Key(bot,stage,y);
        if(wins.getOrDefault(k,0)>0)return false;
        boolean other=anotherReaches(bot,stage,y,builders);
        Long until=benchedUntil.get(k);
        if(until!=null){
            if(now<until&&other)return true;
            // Time is up, or nobody else is getting there any more: try again from scratch.
            benchedUntil.remove(k);fails.remove(k);
            return false;
        }
        if(fails.getOrDefault(k,0)>=FAILS_TO_BENCH&&other){benchedUntil.put(k,now+BENCH_TICKS);return true;}
        return false;
    }
    /** Builders (from the given set) that have placed a block in this layer. */
    public List<UUID> reaching(int stage,int y,Collection<UUID> builders){
        var out=new ArrayList<UUID>();
        for(UUID b:builders)if(wins.getOrDefault(new Key(b,stage,y),0)>0)out.add(b);
        return out;
    }
    private boolean anotherReaches(UUID bot,int stage,int y,Collection<UUID> builders){
        for(UUID b:builders)if(!b.equals(bot)&&wins.getOrDefault(new Key(b,stage,y),0)>0)return true;
        return false;
    }
    public void forget(UUID bot){
        fails.keySet().removeIf(k->k.bot.equals(bot));
        wins.keySet().removeIf(k->k.bot.equals(bot));
        benchedUntil.keySet().removeIf(k->k.bot.equals(bot));
    }
}
