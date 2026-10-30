package dev.companions.survival;

public final class SurvivalPolicy {
    private SurvivalPolicy() {}
    public static boolean shouldRetreat(float health,boolean hasAttacker,int currentTick,int lastHurtTick){
        return health<=5&&hasAttacker&&currentTick-lastHurtTick>=0&&currentTick-lastHurtTick<100;
    }
}
