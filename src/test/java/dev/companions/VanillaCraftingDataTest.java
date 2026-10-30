package dev.companions;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.companions.crafting.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Loads actual vanilla block/item tags and recipes, without creating or changing a world. */
class VanillaCraftingDataTest {
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var pack=new VanillaPackResourcesBuilder().pushJarResources().exposeNamespace("minecraft")
            .build(new PackLocationInfo("vanilla",Component.literal("Vanilla test data"),PackSource.BUILT_IN,Optional.empty()));
        try(var manager=new MultiPackResourceManager(PackType.SERVER_DATA,List.of(pack))){bind(manager,BuiltInRegistries.BLOCK);bind(manager,BuiltInRegistries.ITEM);}
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    private static <T> void bind(MultiPackResourceManager manager,Registry<T> registry){
        var tags=TagLoader.loadTagsForRegistry(manager,registry.key(),(id,required)->registry.get(id));
        registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(),tags)).apply();
    }
    CraftingRecipe recipe(String name)throws Exception{
        var stream=getClass().getResourceAsStream("/data/minecraft/recipe/"+name+".json");assertNotNull(stream);
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            return (CraftingRecipe)Recipe.CODEC.parse(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE),JsonParser.parseReader(reader)).getOrThrow();
        }
    }
    @ParameterizedTest @CsvSource({"stone,wooden_pickaxe","iron_ore,stone_pickaxe","deepslate_iron_ore,stone_pickaxe","diamond_ore,iron_pickaxe","ancient_debris,diamond_pickaxe","obsidian,diamond_pickaxe","anvil,wooden_pickaxe","oak_log,wooden_axe","dirt,wooden_shovel"})
    void selectsMinimumWorkingToolFromActual26_2Tags(String block,String tool){
        var state=BuiltInRegistries.BLOCK.getValue(Identifier.parse(block)).defaultBlockState();
        assertSame(BuiltInRegistries.ITEM.getValue(Identifier.parse(tool)),ToolRequirements.cheapest(state));
        if(state.requiresCorrectToolForDrops())assertTrue(new ItemStack(ToolRequirements.cheapest(state)).isCorrectToolForDrops(state));
    }
    @Test void wrongToolTierCannotHarvestIronDiamondOrAncientDebris(){
        assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()));
        assertFalse(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()));
        assertFalse(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(Blocks.ANCIENT_DEBRIS.defaultBlockState()));
        assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(Blocks.ANCIENT_DEBRIS.defaultBlockState()));
    }
    @ParameterizedTest @CsvSource({"wooden_pickaxe,oak_planks,3,stick,2","stone_pickaxe,cobblestone,3,stick,2","iron_pickaxe,iron_ingot,3,stick,2","diamond_pickaxe,diamond,3,stick,2","wooden_axe,oak_planks,3,stick,2","wooden_shovel,oak_planks,1,stick,2","wooden_hoe,oak_planks,2,stick,2","crafting_table,oak_planks,4,stick,0","furnace,cobblestone,8,stick,0"})
    void supplyMaterialCountsMatchVanillaRecipes(String output,String first,int count,String second,int other)throws Exception{
        var recipe=recipe(output);var ingredients=recipe.placementInfo().ingredients();
        var stacks=List.of(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(first)),count),new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(second)),other));
        assertNotNull(IngredientAllocator.allocate(ingredients.size(),new int[]{count,other},(n,s)->ingredients.get(n).test(stacks.get(s))));
        assertNull(IngredientAllocator.allocate(ingredients.size(),new int[]{count-1,other},(n,s)->ingredients.get(n).test(stacks.get(s))));
        assertTrue(recipe.assemble(CraftingInput.EMPTY).is(BuiltInRegistries.ITEM.getValue(Identifier.parse(output))));
    }
    @Test void logsAndSticksProduceWholeVanillaBatches()throws Exception{
        assertEquals(4,recipe("oak_planks").assemble(CraftingInput.EMPTY).getCount());
        assertEquals(4,recipe("stick").assemble(CraftingInput.EMPTY).getCount());
    }
}
