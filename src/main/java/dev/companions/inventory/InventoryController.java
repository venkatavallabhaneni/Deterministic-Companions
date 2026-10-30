package dev.companions.inventory;

import dev.companions.building.BuildingBlockSelector;
import dev.companions.player.CompanionPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantments;
import java.util.*;

public final class InventoryController {
    private final CompanionPlayer p;
    /** When set, the only item navigation may spend on pillars/bridges (temporary schematic scaffolding). */
    public String constructionOverride;
    private final Map<String,Integer> automaticGrants=new HashMap<>();
    public boolean swordDuty;
    /** User-requested issued equipment: reuse a diamond sword, issue one only if missing. */
    public void diamondSword(){
        if(find("minecraft:diamond_sword")<0&&p.getOffhandItem().is(Items.DIAMOND_SWORD)){
            ItemStack old=p.getMainHandItem();p.getInventory().setItem(p.getInventory().getSelectedSlot(),p.getOffhandItem());p.setItemSlot(EquipmentSlot.OFFHAND,old);
        }
        int slot=IssuedSword.ensure(p.getInventory().getNonEquipmentItems(),p.getInventory().getSelectedSlot(),s->p.drop(s,false,false));
        if(p.getInventory().getSelectedSlot()==slot)return;
        select(slot);
    }
    /** Supplies the deterministic guard loadout.  The arrow is intentionally a
     * real inventory item: Infinity still requires one arrow to be present. */
    public boolean rangedSupplies(){
        int slot=find("minecraft:bow");
        if(slot<0){
            ItemStack bow=new ItemStack(Items.BOW);
            var enchantments=p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            bow.enchant(enchantments.getOrThrow(Enchantments.POWER),5);
            bow.enchant(enchantments.getOrThrow(Enchantments.INFINITY),1);
            if(!grant(List.of(bow)))return false;
            slot=find("minecraft:bow");
        } else {
            // Upgrade an existing bow in place; this does not create a second bow.
            ItemStack bow=p.getInventory().getItem(slot);
            var enchantments=p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            bow.enchant(enchantments.getOrThrow(Enchantments.POWER),5);
            bow.enchant(enchantments.getOrThrow(Enchantments.INFINITY),1);
        }
        if(count("minecraft:arrow")<1 && !grant(List.of(new ItemStack(Items.ARROW,1))))return false;
        p.inventoryMenu.broadcastChanges();
        return slot>=0 || find("minecraft:bow")>=0;
    }
    public boolean equipBow(){int slot=find("minecraft:bow");if(slot<0)return false;select(slot);return true;}
    public InventoryController(CompanionPlayer p) { this.p=p; }
    public boolean grant(List<ItemStack> stacks){
        boolean result=MaterialGrants.insert(p.getInventory().getNonEquipmentItems(),stacks);
        if(result){
            for(ItemStack stack:stacks)if(!stack.isEmpty())automaticGrants.merge(id(stack),stack.getCount(),Integer::sum);
            p.inventoryMenu.broadcastChanges();
        }
        return result;
    }
    /** Number of items supplied by an automated give-style grant since this controller was created. */
    public int automaticGranted(String itemId){return automaticGrants.getOrDefault(itemId,0);}
    public static String id(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }
    public void select(int slot) {
        if (slot<0 || slot>=36) return;
        var inv=p.getInventory();
        if(slot!=inv.getSelectedSlot())p.stopUsingItem();
        if (slot<9) inv.setSelectedSlot(slot);
        else { int held=inv.getSelectedSlot(); ItemStack old=inv.getItem(held); inv.setItem(held,inv.getItem(slot)); inv.setItem(slot,old); }
        p.inventoryMenu.broadcastChanges();
    }
    public void stowHeld(){
        p.stopUsingItem();p.setShiftKeyDown(false);
        int next=EquipmentSelection.stow(p.getInventory().getNonEquipmentItems(),p.getInventory().getSelectedSlot());
        p.getInventory().setSelectedSlot(next);p.inventoryMenu.broadcastChanges();
    }
    public int find(String id) { for(int i=0;i<36;i++) if(id(p.getInventory().getItem(i)).equals(id) && !p.getInventory().getItem(i).isEmpty()) return i; return -1; }
    public int count(String id) { int n=0; for(int i=0;i<36;i++) { var s=p.getInventory().getItem(i); if(id(s).equals(id)) n+=s.getCount(); } return n; }
    public boolean canAccept(ItemStack incoming) {
        for(int i=0;i<36;i++) {var s=p.getInventory().getItem(i);if(s.isEmpty() || ItemStack.isSameItemSameComponents(s,incoming) && s.getCount()<s.getMaxStackSize())return true;}
        return false;
    }
    public int buildingSlot() {
        if(constructionOverride!=null)return find(constructionOverride);
        List<BuildingBlockSelector.Candidate> list=new ArrayList<>();
        for(int i=0;i<36;i++) {var s=p.getInventory().getItem(i); if(s.getItem() instanceof BlockItem) list.add(new BuildingBlockSelector.Candidate(i,id(s),s.getCount()));}
        return BuildingBlockSelector.select(list);
    }
    /** Item supplied when navigation runs low on construction blocks. */
    public Item constructionItem(){
        return constructionOverride==null?Items.COBBLESTONE:BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(constructionOverride)).orElse(Items.COBBLESTONE);
    }
    public int buildingCount() { if(constructionOverride!=null)return count(constructionOverride); int n=0; for(int i=0;i<36;i++) { var s=p.getInventory().getItem(i); if(dev.companions.world.BlockSafetyPolicy.disposable(id(s))) n+=s.getCount(); } return n; }
    public void tool(BlockState state) {
        double best=-Double.MAX_VALUE; int slot=-1;
        for(int i=0;i<36;i++) {
            ItemStack s=p.getInventory().getItem(i);
            double score=ToolScoring.score(s.isCorrectToolForDrops(state),miningSpeed(s,state),s.getMaxDamage()-s.getDamageValue(),s.getMaxDamage(),s.getEnchantments().size());
            if(score>best) {best=score;slot=i;}
        }
        select(slot);
    }
    public boolean hasHarvestTool(BlockState state) {
        if(!state.requiresCorrectToolForDrops())return true;
        for(int i=0;i<36;i++) {
            var stack=p.getInventory().getItem(i);
            if(stack.isCorrectToolForDrops(state) && (!stack.isDamageableItem() || stack.getMaxDamage()-stack.getDamageValue()>1))return true;
        }
        return false;
    }
    private double miningSpeed(ItemStack stack,BlockState state) {
        double speed=stack.getDestroySpeed(state);
        if(speed>1)for(var entry:stack.getEnchantments().entrySet())if(entry.getKey().is(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY))speed+=entry.getIntValue()*entry.getIntValue()+1;
        return speed;
    }
    public double estimateBreakTicks(BlockState state,net.minecraft.core.BlockPos pos) {
        double best=1;
        for(int i=0;i<36;i++) {
            var stack=p.getInventory().getItem(i);
            if(stack.isDamageableItem() && stack.getMaxDamage()-stack.getDamageValue()<=1)continue;
            double speed=miningSpeed(stack,state)/(state.requiresCorrectToolForDrops()&&!stack.isCorrectToolForDrops(state)?100:30);
            best=Math.max(best,speed*30);
        }
        return Math.max(0,state.getDestroySpeed(p.level(),pos))*30/best;
    }
    private double attribute(ItemStack s, EquipmentSlot slot, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        double[] value={0}; s.forEachModifier(slot,(a,m) -> {if(a.equals(attribute)) value[0]+=m.amount();}); return value[0];
    }
    public void weapon() {
        if(swordDuty){diamondSword();return;}
        double best=0; int slot=-1;
        for(int i=0;i<36;i++) {double score=attribute(p.getInventory().getItem(i),EquipmentSlot.MAINHAND,Attributes.ATTACK_DAMAGE); if(score>best){best=score;slot=i;}}
        select(slot);
    }
    public void armor() {
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            double best=attribute(p.getItemBySlot(slot),slot,Attributes.ARMOR); int selected=-1;
            for(int i=0;i<36;i++) {
                var s=p.getInventory().getItem(i); var equipment=s.get(DataComponents.EQUIPPABLE);
                if(equipment==null || equipment.slot()!=slot) continue;
                double score=attribute(s,slot,Attributes.ARMOR)+attribute(s,slot,Attributes.ARMOR_TOUGHNESS)*0.1;
                if(score>best) {best=score;selected=i;}
            }
            if(selected>=0) {ItemStack old=p.getItemBySlot(slot); p.setItemSlot(slot,p.getInventory().getItem(selected)); p.getInventory().setItem(selected,old);}
        }
    }
    public boolean eat() {
        if(p.isUsingItem()) return true;
        if(!p.canEat(false)) return false;
        for(int i=0;i<36;i++) {
            var s=p.getInventory().getItem(i);
            if(s.has(DataComponents.FOOD) && !Set.of("minecraft:rotten_flesh","minecraft:spider_eye","minecraft:poisonous_potato","minecraft:pufferfish","minecraft:chorus_fruit").contains(id(s))) {
                if(swordDuty){
                    ItemStack old=p.getOffhandItem();p.setItemSlot(EquipmentSlot.OFFHAND,s);p.getInventory().setItem(i,old);
                    diamondSword();p.gameMode.useItem(p,p.level(),p.getOffhandItem(),net.minecraft.world.InteractionHand.OFF_HAND);
                }else{select(i);p.gameMode.useItem(p,p.level(),p.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);}
                return p.isUsingItem();
            }
        }
        return false;
    }
    public void transfer(String id,int amount,net.minecraft.server.level.ServerPlayer recipient) {
        if(count(id)<amount) throw new IllegalArgumentException("Not enough "+id);
        for(int i=0;i<36 && amount>0;i++) {
            var s=p.getInventory().getItem(i); if(!id(s).equals(id)) continue;
            int n=Math.min(amount,s.getCount()); ItemStack moved=s.split(n); amount-=n;
            if(recipient==null) p.drop(moved,false,false);
            else {recipient.getInventory().add(moved); if(!moved.isEmpty()) recipient.drop(moved,false,false);}
        }
        p.inventoryMenu.broadcastChanges();
    }
    public String describe() {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<p.getInventory().getContainerSize();i++) {var s=p.getInventory().getItem(i); if(!s.isEmpty()) out.append(i).append(": ").append(id(s)).append(" x").append(s.getCount()).append('\n');}
        return out.isEmpty()?"Inventory empty":out.toString();
    }
}
