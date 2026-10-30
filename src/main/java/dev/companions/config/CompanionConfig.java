package dev.companions.config;

public final class CompanionConfig {
    /** Central hard cap; inactive records never consume a slot. */
    public static final int MAX_ACTIVE = 4;
    public static final int MAX_FLYERS = 1;
    public static final int SEARCH_RADIUS = 32;
    public static final int MAX_SEARCH_NODES = 2048;
    public static final int NODES_PER_TICK = 96;
    public boolean breakBlocks = true;
    public boolean placeBlocks = true;
    public boolean bridge = true;
    public boolean sprint = true;
    public float maxHealth = HealthPolicy.DEFAULT;
    public boolean pvp = false;
    public boolean friendlyFire = false;
    public int maxDistance = 256;
}
