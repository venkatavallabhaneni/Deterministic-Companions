package dev.companions.task;

import dev.companions.combat.GuardArea;
import dev.companions.combat.CombatController;
import dev.companions.player.CompanionController;
import dev.companions.navigation.WorldNeighbors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import java.util.Comparator;

/** Persistent area duty: targets monsters inside the cuboid and returns to a safe post. */
public final class AreaGuardTask extends BaseTask {
    public final GuardArea area;
    public final String dimension;
    private LivingEntity threat;
    private BlockPos post;
    private final int postIndex,guardCount;
    private int ticks;
    private final java.util.Map<java.util.UUID,Integer> blockedUntil=new java.util.HashMap<>();
    private int lastMaterials=-1;
    public AreaGuardTask(CompanionController c,GuardArea area){this(c,area,0,1);}
    public AreaGuardTask(CompanionController c,GuardArea area,int postIndex,int guardCount){super(c,"GUARD AREA "+area.describe());this.area=area;this.postIndex=postIndex;this.guardCount=guardCount;dimension=c.player.level().dimension().identifier().toString();}
    @Override public void start(){c.combat.passive=false;c.combat.rangedDuty=postIndex < (guardCount+1)/2;c.combat.formation(postIndex,guardCount);c.inventory.swordDuty=true;c.inventory.diamondSword();c.inventory.rangedSupplies();c.issueGuardDirt();}
    @Override public void tick(){
        if(!(c.combat.rangedDuty&&c.player.isUsingItem()))c.inventory.diamondSword();ticks++;
        if(ticks%100==1)c.issueGuardDirt(); // Retry a full inventory, never replenish an issued stack.
        int materials=c.inventory.buildingCount();if(materials>lastMaterials)blockedUntil.clear();lastMaterials=materials;
        blockedUntil.values().removeIf(until->until<=ticks);
        if(!dimension.equals(c.player.level().dimension().identifier().toString())){c.navigation.stop();status="GUARD AREA waiting in assigned dimension "+dimension;return;}
        if(threat!=null&&c.navigation.failed()){
            if(blockedUntil.size()>=64)blockedUntil.clear();blockedUntil.put(threat.getUUID(),ticks+200);threat=null;c.navigation.stop();
        }
        if(ticks%10==1){
            var box=new AABB(area.minX(),area.minY(),area.minZ(),(double)area.maxX()+1,(double)area.maxY()+1,(double)area.maxZ()+1);
            threat=c.player.level().getEntitiesOfClass(LivingEntity.class,box,this::eligible).stream()
                .filter(e->!blockedUntil.containsKey(e.getUUID())).min(Comparator.comparingDouble((LivingEntity e)->c.player.distanceToSqr(e)).thenComparingInt(Entity::getId)).orElse(null);
        }
        if(threat!=null&&eligible(threat)&&!c.navigation.failed()){
            status="GUARD AREA "+area.describe()+" — attacking "+threat.getName().getString()+"; building blocks: "+materials;
            if(c.combat.engage(threat))return;
        }
        threat=null;c.player.stopUsingItem();
        if(ticks%40==1){var next=findPost();if(!java.util.Objects.equals(next,post)){post=next;c.navigation.stop();}}
        if(post==null){c.navigation.stop();status="GUARD AREA "+area.describe()+" — waiting for a safe loaded post";return;}
        status="GUARD AREA "+area.describe();
        if(c.player.position().distanceToSqr(Vec3.atBottomCenterOf(post))>0.5)c.navigation.target(Vec3.atBottomCenterOf(post),0.5);else c.navigation.stop();
    }
    private boolean eligible(LivingEntity e){return e.getType().getCategory()==MobCategory.MONSTER&&c.combat.allowed(e)&&area.contains(e.getX(),e.getY(),e.getZ());}
    private BlockPos findPost(){
        c.navigation.world.newSearch();
        boolean wideX=area.maxX()-area.minX()>=area.maxZ()-area.minZ();
        double fraction=(postIndex+0.5)/guardCount;
        int x=(int)Math.floor(area.minX()+(area.maxX()-area.minX()+1)*(wideX?fraction:0.5));
        int z=(int)Math.floor(area.minZ()+(area.maxZ()-area.minZ()+1)*(wideX?0.5:fraction));
        int y=Math.clamp(c.player.getBlockY(),area.minY(),area.maxY());
        // At most 2,304 candidates every two seconds, with loaded-chunk and collision checks.
        for(int dy=0;dy<=area.maxY()-area.minY();dy++)for(int sign:new int[]{1,-1})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            BlockPos p=new BlockPos(Math.clamp(x+dx,area.minX(),area.maxX()),y+sign*dy,Math.clamp(z+dz,area.minZ(),area.maxZ()));
            if(area.contains(p.getX(),p.getY(),p.getZ())&&c.navigation.world.stand(WorldNeighbors.cell(p)))return p;
        }
        var candidate=new BlockPos(x,y,z);var cell=WorldNeighbors.cell(candidate);
        if(c.config.placeBlocks&&c.config.bridge&&c.inventory.buildingCount()>0&&c.navigation.world.clear(cell)
            &&c.navigation.world.safe(cell.add(0,-1,0))&&c.navigation.world.replaceable(cell.add(0,-1,0)))return candidate;
        return null;
    }
}
