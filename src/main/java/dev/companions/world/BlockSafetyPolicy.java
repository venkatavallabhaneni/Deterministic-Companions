package dev.companions.world;

import java.util.Set;

/** Conservative whitelist for automated construction, conservative exclusions for digging. */
public final class BlockSafetyPolicy {
    private static final Set<String> BUILDING=Set.of("cobblestone","cobbled_deepslate","dirt","coarse_dirt","stone","deepslate","netherrack","andesite","diorite","granite","tuff","end_stone","oak_planks","spruce_planks","birch_planks","jungle_planks","acacia_planks","dark_oak_planks","mangrove_planks","cherry_planks","pale_oak_planks","bamboo_planks","crimson_planks","warped_planks");
    private static final Set<String> PROTECTED=Set.of("bedrock","barrier","end_portal","end_portal_frame","nether_portal","spawner","trial_spawner","vault","beacon","dragon_egg","reinforced_deepslate","diamond_block","emerald_block","netherite_block","gold_block","iron_block","raw_iron_block","raw_gold_block","raw_copper_block","ancient_debris","respawn_anchor","lodestone","enchanting_table");
    private static String vanilla(String id) { return id.startsWith("minecraft:") ? id.substring(10) : ""; }
    private static final Set<String> ANVILS=Set.of("anvil","chipped_anvil","damaged_anvil");
    public static boolean disposable(String id) { return BUILDING.contains(vanilla(id)); }
    public static boolean mayBreak(String id, boolean blockEntity, double hardness) {
        String path=vanilla(id);
        return !path.isEmpty() && !blockEntity && hardness>=0 && !PROTECTED.contains(path)
            && !path.endsWith("_bed") && !path.contains("shulker") && !path.contains("chest") && !path.endsWith("_table")
            && !Set.of("anvil","chipped_anvil","damaged_anvil","grindstone","loom","stonecutter","bell","conduit").contains(path);
    }
    /** Explicit mining/digging may harvest anvils or ancient debris; navigation protects both. */
    public static boolean mayBreak(String id, boolean blockEntity, double hardness, boolean explicitTarget) {
        return mayBreak(id,blockEntity,hardness) || explicitTarget && !blockEntity && hardness>=0 && (ANVILS.contains(vanilla(id))||vanilla(id).equals("ancient_debris"));
    }
    public static boolean dangerous(String id) {
        return Set.of("lava","fire","soul_fire","cactus","magma_block","campfire","soul_campfire","sweet_berry_bush","powder_snow","wither_rose","pointed_dripstone").contains(vanilla(id));
    }
}
