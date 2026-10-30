package dev.companions.inventory;

public final class ToolScoring {
    public static double score(boolean correct, double speed, int remaining, int maximum, int enchantments) {
        if (maximum>0 && remaining<=1) return -1;
        return (correct?1000:0)+speed*20+enchantments*2+(maximum==0?1:(double)remaining/maximum);
    }
}
