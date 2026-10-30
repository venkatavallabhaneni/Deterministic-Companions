package dev.companions;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the shipped Minecraft 26.2 properties and datapack, not a re-created loot table. */
class MinecraftMiningDataTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private com.google.gson.JsonObject resource(String path) throws Exception {
        var stream=getClass().getResourceAsStream("/data/minecraft/"+path);
        assertNotNull(stream,"Missing Minecraft data: "+path);
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {return JsonParser.parseReader(reader).getAsJsonObject();}
    }

    @Test void actualAnvilStatesRequireHarvestToolAndHaveNoStoredBlockEntity() {
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.ANVIL,Blocks.CHIPPED_ANVIL,Blocks.DAMAGED_ANVIL}) {
            var state=block.defaultBlockState();
            assertTrue(state.requiresCorrectToolForDrops());
            assertFalse(state.hasBlockEntity());
            assertFalse(state.isAir());
        }
    }
    @Test void actualPickaxeTagIncludesAllAnvilVariants() throws Exception {
        var pickaxe=resource("tags/block/mineable/pickaxe.json").getAsJsonArray("values");
        assertTrue(pickaxe.asList().stream().anyMatch(v -> v.getAsString().equals("#minecraft:anvil")));
        var anvils=resource("tags/block/anvil.json").getAsJsonArray("values");
        for(String variant:new String[]{"anvil","chipped_anvil","damaged_anvil"})assertTrue(anvils.asList().stream().anyMatch(v -> v.getAsString().equals("minecraft:"+variant)));
    }
    @ParameterizedTest @ValueSource(strings={"anvil","chipped_anvil","damaged_anvil"})
    void actualLootTableDropsTheMatchingVariant(String variant) throws Exception {
        var entry=resource("loot_table/blocks/"+variant+".json").getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("minecraft:item",entry.get("type").getAsString());
        assertEquals("minecraft:"+variant,entry.get("name").getAsString());
    }
}
