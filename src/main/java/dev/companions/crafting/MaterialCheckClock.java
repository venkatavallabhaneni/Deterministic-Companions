package dev.companions.crafting;

/** One check per companion per 100 server ticks, shared across nested supply tasks. */
public final class MaterialCheckClock {
    public static final int INTERVAL=100;
    private long next;
    public boolean due(long tick){if(tick<next)return false;next=tick+INTERVAL;return true;}
}
