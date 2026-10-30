package dev.companions.player;

import dev.companions.config.CompanionConfig;

public final class CompanionLimit {
    private CompanionLimit() {}
    public static void check(int active) {
        if (active >= CompanionConfig.MAX_ACTIVE)
            throw new IllegalStateException("Maximum companions reached: "+CompanionConfig.MAX_ACTIVE+"/"+CompanionConfig.MAX_ACTIVE);
    }
    public static void checkFlyer(int active) {
        if(active>=CompanionConfig.MAX_FLYERS)
            throw new IllegalStateException("Maximum flying companions reached: 1/1");
    }
}
