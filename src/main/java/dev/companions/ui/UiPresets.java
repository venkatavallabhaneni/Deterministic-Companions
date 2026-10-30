package dev.companions.ui;

import java.util.List;

/** The UI deliberately reuses ordinary /bot handlers, including bulk semantics. */
public final class UiPresets {
    private UiPresets() {}
    public static final List<String> MINERALS=List.of("stone","coal_ore","iron_ore","copper_ore","gold_ore","redstone_ore","lapis_ore","diamond_ore","emerald_ore","deepslate_iron_ore","ancient_debris","oak_log");
    public static final List<String> SHAPES=List.of("bridge","wall","floor","pillar","stairs");
    public static final List<String> FLAGS=List.of("sprint","breakBlocks","placeBlocks","bridge","pvp","friendlyFire");
    public static String mine(String selection,String mineral,int amount) {return "bot mine "+selection+" minecraft:"+mineral+" "+amount;}
    public static String build(String selection,String shape,int size) {
        return "bot build "+selection+" "+shape+" "+size+(shape.equals("wall")?" 3":shape.equals("floor")?" "+size:"");
    }
}
