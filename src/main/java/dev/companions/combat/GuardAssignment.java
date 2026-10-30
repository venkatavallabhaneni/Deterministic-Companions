package dev.companions.combat;

import dev.companions.config.CompanionConfig;
import java.util.List;

/** Validates an entire assignment before any current task is cancelled. */
public final class GuardAssignment {
    public static <T> List<T> choose(List<T> orderedEligible,int count){
        if(count<1||count>CompanionConfig.MAX_ACTIVE)throw new IllegalArgumentException("Choose 1–"+CompanionConfig.MAX_ACTIVE+" guards");
        if(orderedEligible.size()<count)throw new IllegalArgumentException("Not enough available companions in this dimension: need "+count+", available "+orderedEligible.size()+". Spawn companions first or stop another area assignment.");
        return List.copyOf(orderedEligible.subList(0,count));
    }
}
