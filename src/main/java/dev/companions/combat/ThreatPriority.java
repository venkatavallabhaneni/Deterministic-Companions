package dev.companions.combat;

public final class ThreatPriority {
    public static int score(boolean attacking,boolean projectileOwner,boolean targeting,boolean closeHostile,boolean selfDefense) {
        return attacking?500:projectileOwner?400:targeting?300:closeHostile?200:selfDefense?100:0;
    }
}
