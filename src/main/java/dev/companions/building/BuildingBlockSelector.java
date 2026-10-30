package dev.companions.building;

import dev.companions.world.BlockSafetyPolicy;
import java.util.List;

public final class BuildingBlockSelector {
    public record Candidate(int slot, String id, int count) {}
    public static int select(List<Candidate> candidates) {
        return candidates.stream().filter(c -> c.count>0 && BlockSafetyPolicy.disposable(c.id))
            .min(java.util.Comparator.comparingInt((Candidate c) -> priority(c.id)).thenComparingInt(Candidate::slot))
            .map(Candidate::slot).orElse(-1);
    }
    private static int priority(String id) { return id.equals("minecraft:cobblestone")?0:id.equals("minecraft:cobbled_deepslate")?1:id.equals("minecraft:dirt")?2:3; }
}
