package dev.companions.player;

import dev.companions.config.CompanionConfig;
import dev.companions.task.TaskController;
import net.minecraft.core.BlockPos;
import java.util.UUID;

public final class CompanionController {
    public final CompanionPlayer player;
    public final UUID owner;
    public boolean flyer;
    public CompanionConfig config = new CompanionConfig();
    public BlockPos home;
    public String homeDimension;
    public boolean guardDirtIssued;
    /** One-command group assignment; consumed by Protect/Guard start. */
    public Boolean rangedDutyOverride;
    public Integer formationIndexOverride,formationCountOverride;
    public void issueGuardDirt(){
        if(!guardDirtIssued){guardDirtIssued=dev.companions.inventory.GuardSupplies.issueOnce(player.getInventory().getNonEquipmentItems(),false);player.inventoryMenu.broadcastChanges();}
    }
    public final TaskController tasks;
    public final dev.companions.inventory.InventoryController inventory;
    public final dev.companions.inventory.FlyingLoadout flyingLoadout;
    public final dev.companions.world.WorldActionController actions;
    public final dev.companions.navigation.NavigationController navigation;
    public final dev.companions.combat.CombatController combat;
    public final dev.companions.survival.SurvivalController survival;
    public final dev.companions.crafting.CraftingController crafting;
    public final dev.companions.crafting.WorkstationController workstations;
    public final dev.companions.world.ResourceClaims resources;
    public final dev.companions.building.ConstructionPolicy construction=new dev.companions.building.ConstructionPolicy();
    private boolean survivalControlled;
    public CompanionController(CompanionPlayer player, UUID owner,dev.companions.world.ResourceClaims resources) {
        this.resources=resources;
        this.player=player; this.owner=owner;
        home=player.blockPosition(); homeDimension=player.level().dimension().identifier().toString();
        inventory=new dev.companions.inventory.InventoryController(player);
        flyingLoadout=new dev.companions.inventory.FlyingLoadout(this);
        actions=new dev.companions.world.WorldActionController(this);
        navigation=new dev.companions.navigation.NavigationController(this);
        combat=new dev.companions.combat.CombatController(this);
        survival=new dev.companions.survival.SurvivalController(this);
        crafting=new dev.companions.crafting.CraftingController(this);
        workstations=new dev.companions.crafting.WorkstationController(this);
        tasks=new TaskController(navigation::stop);
    }
    public boolean swordDuty(){return tasks.current() instanceof dev.companions.task.ProtectTask || tasks.current() instanceof dev.companions.task.GuardTask || tasks.current() instanceof dev.companions.task.AreaGuardTask;}
    private void enforceDutyHand(){
        // A bow draw legitimately occupies the main hand. Do not reselect the
        // sword every tick or the draw would be cancelled before release.
        if(inventory.swordDuty && !(combat.rangedDuty && player.isUsingItem()))inventory.diamondSword();
    }
    public void tick() {
        if(flyer&&player.tickCount%20==0)flyingLoadout.maintain();
        inventory.swordDuty=swordDuty();enforceDutyHand();
        boolean emergency=survival.tick();
        if(!emergency){if(survivalControlled)navigation.stop();tasks.tick();}
        survivalControlled=emergency;navigation.tick();
        if(swordDuty())enforceDutyHand();
    }
    public void health(float value,boolean heal){
        config.maxHealth=dev.companions.config.HealthPolicy.validate(value);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(value);
        player.setHealth(heal?player.getMaxHealth():Math.min(player.getHealth(),player.getMaxHealth()));
    }
    public boolean resourceAvailable(BlockPos pos){return resources.available(player.getUUID(),player.level().dimension().identifier().toString(),pos.asLong(),player.level().getGameTime());}
    public boolean workAvailable(BlockPos pos){return resources.workAvailable(player.getUUID(),player.level().dimension().identifier().toString(),pos.asLong(),player.level().getGameTime());}
    public boolean canWorkFrom(BlockPos pos){return resources.canWorkFrom(player.getUUID(),player.level().dimension().identifier().toString(),pos.asLong(),player.level().getGameTime());}
    public boolean workFrom(BlockPos pos){return resources.workFrom(player.getUUID(),player.level().dimension().identifier().toString(),pos.asLong(),player.level().getGameTime());}
    public boolean claimResource(BlockPos pos){return resources.claimWork(player.getUUID(),player.level().dimension().identifier().toString(),pos.asLong(),player.level().getGameTime());}
    public void releaseResource(){resources.release(player.getUUID());}
    public String status() {
        return player.getPlainTextName()+(flyer?" (FLYER)":"")+"\nTask: "+tasks.status()+"\nHealth: "+player.getHealth()+"/"+player.getMaxHealth()
            +"\nFood: "+player.getFoodData().getFoodLevel()+"/20\nPosition: "+player.blockPosition().toShortString()+"\nPath: "+navigation.remaining()+" nodes remaining ("+navigation.status()+")";
    }
}
