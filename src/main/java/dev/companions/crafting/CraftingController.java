package dev.companions.crafting;

import dev.companions.player.CompanionController;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Uses the server's loaded recipes and vanilla crafting result slots (including remainders). */
public final class CraftingController {
    public enum Result { CRAFTED, MISSING_MATERIALS, NEED_TABLE, PREPARING, STATION_BLOCKED, NO_RECIPE, DENIED, INVENTORY_FULL }
    private final CompanionController c;
    private final Map<Item,List<CraftingRecipe>> recipes=new HashMap<>();
    private Object recipeManager;
    private final MaterialCheckClock materialClock=new MaterialCheckClock();
    public CraftingController(CompanionController c){this.c=c;}
    private List<CraftingRecipe> recipes(Item item) {
        var manager=c.player.level().getServer().getRecipeManager();
        var loaded=manager.getRecipes();
        if(recipeManager!=loaded){recipes.clear();recipeManager=loaded;}
        return recipes.computeIfAbsent(item,key->manager.getRecipes().stream().sorted(Comparator.comparing(r->r.id().identifier().toString()))
            .map(RecipeHolder::value).filter(r->r instanceof ShapedRecipe||r instanceof ShapelessRecipe)
            .map(r->(CraftingRecipe)r).filter(r->r.assemble(CraftingInput.EMPTY).is(item)).toList());
    }
    public Result craftOnce(Item output) {
        if(recipes(output).isEmpty())return Result.NO_RECIPE;
        if(output!=Items.CRAFTING_TABLE){var ready=prepareTable();if(ready!=Result.CRAFTED)return ready;}
        return craftInMenu(output,output!=Items.CRAFTING_TABLE);
    }
    /** Only the private table itself is bootstrapped in the player's 2x2 grid. */
    public Result prepareTable(){
        return switch(c.workstations.ensure(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)){
            case READY -> Result.CRAFTED;
            case MOVING -> Result.PREPARING;
            case BLOCKED -> Result.STATION_BLOCKED;
            case NEED_ITEM -> {
                var result=craftInMenu(Items.CRAFTING_TABLE,false);
                yield result==Result.CRAFTED?Result.PREPARING:result;
            }
        };
    }
    private Result craftInMenu(Item output,boolean privateTable) {
        List<CraftingRecipe> options=recipes(output);if(options.isEmpty())return Result.NO_RECIPE;
        if(c.player.containerMenu instanceof AbstractCraftingMenu current&&!recoverInputs(current))return Result.INVENTORY_FULL;
        if(c.player.containerMenu!=c.player.inventoryMenu&&!recoverInputs(c.player.inventoryMenu))return Result.INVENTORY_FULL;
        long now=c.player.level().getGameTime();
        if(materialClock.due(now)){
            List<ItemStack> best=null;
            for(var recipe:options){
                var ingredients=recipe instanceof ShapedRecipe shaped?shaped.getIngredients().stream().flatMap(Optional::stream).toList():recipe.placementInfo().ingredients();
                var missing=RecipeMaterials.missing(ingredients,c.player.getInventory().getNonEquipmentItems());
                if(missing!=null&&(best==null||missing.size()<best.size()))best=missing;
            }
            if(best!=null&&!best.isEmpty()&&!c.inventory.grant(best))return Result.INVENTORY_FULL;
        }
        boolean needsTable=false;
        for(CraftingRecipe recipe:options) {
            List<Optional<Ingredient>> cells;int width,height;
            if(recipe instanceof ShapedRecipe shaped){cells=shaped.getIngredients();width=shaped.getWidth();height=shaped.getHeight();}
            else {cells=recipe.placementInfo().ingredients().stream().map(Optional::of).toList();width=cells.size()>4?3:2;height=(cells.size()+width-1)/width;}
            List<Ingredient> ingredients=cells.stream().flatMap(Optional::stream).toList();
            int[] counts=new int[36];for(int i=0;i<36;i++)counts[i]=c.player.getInventory().getItem(i).getCount();
            int[] slots=IngredientAllocator.allocate(ingredients.size(),counts,(n,s)->ingredients.get(n).test(c.player.getInventory().getItem(s)));
            if(slots==null)continue;
            AbstractCraftingMenu menu;
            if(privateTable||width>2||height>2){
                if(!(c.player.containerMenu instanceof CraftingMenu table)||!c.workstations.ownsOpenTable()){needsTable=true;continue;}
                menu=table;
            }else {if(c.player.containerMenu!=c.player.inventoryMenu)c.player.closeContainer();menu=c.player.inventoryMenu;}
            c.navigation.pause();c.actions.cancelBreak();c.player.stopUsingItem();c.player.setShiftKeyDown(false);
            if(!RecipeMaterials.roomAfterConsumption(c.player.getInventory().getNonEquipmentItems(),slots,recipe.assemble(CraftingInput.EMPTY)))return Result.INVENTORY_FULL;
            var grid=menu.getInputGridSlots();
            if(grid.stream().anyMatch(Slot::hasItem))return Result.DENIED;
            int side=grid.size()==4?2:3,index=0;
            try {
                for(int cell=0;cell<cells.size();cell++)if(cells.get(cell).isPresent()) {
                    ItemStack ingredient=c.player.getInventory().getItem(slots[index++]).split(1);
                    grid.get(cell%width+(cell/width)*side).set(ingredient);
                }
                // slotsChanged is synchronous in vanilla, but fake players have no
                // client recipe packet. Recompute once through the menu before taking.
                if(!grid.isEmpty()){grid.getFirst().set(grid.getFirst().getItem());menu.broadcastChanges();}
                if(!menu.getResultSlot().getItem().is(output))return Result.DENIED;
                ItemStack taken=menu.quickMoveStack(c.player,0);
                return taken.isEmpty()?Result.INVENTORY_FULL:Result.CRAFTED;
            }finally {
                // Recover unused inputs/remainders on denied or partial operations. Never duplicate them.
                for(var slot:grid)if(slot.hasItem()) {
                    ItemStack remainder=slot.remove(slot.getItem().getCount());c.player.getInventory().add(remainder);
                    if(!remainder.isEmpty())c.player.drop(remainder,false,false);
                }
                c.player.inventoryMenu.broadcastChanges();c.inventory.stowHeld();
            }
        }
        return needsTable?Result.NEED_TABLE:Result.MISSING_MATERIALS;
    }
    private boolean recoverInputs(AbstractCraftingMenu menu){
        List<ItemStack> inputs=new ArrayList<>();
        for(var slot:menu.getInputGridSlots())if(slot.hasItem())inputs.add(slot.getItem());
        if(!menu.getCarried().isEmpty())inputs.add(menu.getCarried());
        if(inputs.isEmpty())return true;
        if(!c.inventory.grant(inputs))return false;
        for(var slot:menu.getInputGridSlots())slot.set(ItemStack.EMPTY);
        menu.setCarried(ItemStack.EMPTY);menu.broadcastChanges();return true;
    }
}
