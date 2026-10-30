package dev.companions.combat;

import dev.companions.player.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class CombatController {
    private final CompanionController c;
    public boolean passive;
    public UUID protectedPlayer;
    /** Set only by Protect/GuardArea.  The stable name hash assigns half of a
     * group to ranged duty, so two of four (or one of two) use bows. */
    public boolean rangedDuty;
    private int bowDrawTicks;
    private int bowCooldown;
    private int formationIndex,formationCount=1,routeVariant;
    private UUID routeTarget;
    private UUID explicitMobTarget;
    public CombatController(CompanionController c) {this.c=c;}
    public boolean allowed(LivingEntity e) {
        if(e==c.player || !e.isAlive() || e instanceof CompanionPlayer || e.getUUID().equals(c.owner) || e.getUUID().equals(protectedPlayer)) return false;
        if(c.flyer && e instanceof Mob && e.getUUID().equals(explicitMobTarget))return true;
        if(e instanceof Player p && (!c.config.pvp || p.isCreative() || p.isSpectator())) return false;
        if(!c.config.friendlyFire && (c.player.isAlliedTo(e) || e instanceof TamableAnimal pet && pet.isTame())) return false;
        return true;
    }
    /** A direct player designation may include passive/neutral mobs, never players or companions. */
    public boolean allowedExplicitMob(LivingEntity e){
        return e instanceof Mob && e.isAlive() && !(e instanceof CompanionPlayer) && !e.getUUID().equals(c.owner);
    }
    public void explicitMobTarget(UUID target){explicitMobTarget=target;}
    public LivingEntity threat(LivingEntity protectedEntity,Vec3 center,double radius) {
        var entities=c.player.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(center,center).inflate(radius),this::allowed);
        LivingEntity attacker=protectedEntity==null?null:protectedEntity.getLastHurtByMob();
        boolean recent=protectedEntity!=null && protectedEntity.tickCount-protectedEntity.getLastHurtByMobTimestamp()<200;
        LivingEntity chosen=null;double best=-1;
        for(LivingEntity e:entities.stream().sorted(Comparator.comparingInt(Entity::getId)).limit(64).toList()) {
            boolean targeting=e instanceof Mob m && m.getTarget()==protectedEntity;
            boolean projectile=protectedEntity!=null && protectedEntity.getLastDamageSource()!=null && protectedEntity.getLastDamageSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
            int score=ThreatPriority.score(recent && attacker==e && !projectile,recent && attacker==e && projectile,targeting,e instanceof Monster && e.position().distanceToSqr(center)<36,c.player.getLastHurtByMob()==e && c.player.tickCount-c.player.getLastHurtByMobTimestamp()<100);
            // A ranged hostile is an actionable protection threat throughout
            // the 20-block envelope even before it has landed a hit.
            if(score==0 && e instanceof RangedAttackMob && e.position().distanceToSqr(center)<=400)score=180;
            if(score==0)continue;
            double value=score-e.position().distanceToSqr(center)*0.01;
            if(value>best){best=value;chosen=e;}
        }
        return chosen;
    }
    public LivingEntity nearest() {return threat(c.player,c.player.position(),16);}
    public void formation(int index,int count){formationIndex=index;formationCount=Math.clamp(count,1,4);routeVariant=0;routeTarget=null;}
    public void recoverRoute(){routeVariant++;c.navigation.stop();}
    public boolean engage(LivingEntity target) {
        if(!allowed(target) || passive) return false;
        if(!target.getUUID().equals(routeTarget)){routeTarget=target.getUUID();routeVariant=0;c.navigation.stop();}
        else if(c.navigation.failed()){routeVariant++;c.navigation.stop();}
        if(bowCooldown>0)bowCooldown--;
        if(rangedDuty && target instanceof RangedAttackMob && engageRanged(target))return true;
        c.inventory.weapon();
        c.actions.lookAt(target.getEyePosition());
        if(c.player.distanceToSqr(target)>6.25 || !c.player.hasLineOfSight(target)) {
            c.player.stopUsingItem();
            c.navigation.targetTracking(approach(target,c.player.hasLineOfSight(target)?1.7:1.3),0.7);
            return true;
        }
        c.navigation.pause(); c.actions.lookAt(target.getEyePosition());
        if(c.player.getAttackStrengthScale(0)>=1 && c.player.isWithinEntityInteractionRange(target,0)) {
            c.player.stopUsingItem();c.actions.attack(target);
        } else {
            if(c.player.getOffhandItem().is(Items.SHIELD) && !c.player.isUsingItem()) c.player.startUsingItem(InteractionHand.OFF_HAND);
        }
        return true;
    }
    private boolean engageRanged(LivingEntity target) {
        // Close the distance to the reliable 20-block engagement envelope.
        if(c.player.distanceToSqr(target)>400 || !c.player.hasLineOfSight(target)) {
            if(c.player.isUsingItem())c.player.releaseUsingItem();
            bowDrawTicks=0;c.player.stopUsingItem();
            c.navigation.targetTracking(approach(target,3.5),0.8);
            return true;
        }
        if(!c.inventory.rangedSupplies() || !c.inventory.equipBow()) return false;
        Vec3 aim=predictAim(target);
        c.actions.lookAt(aim);
        if(c.player.isUsingItem()) {
            bowDrawTicks++;
            // A full vanilla bow draw is 20 ticks. Keep correcting for motion
            // while drawing, then let BowItem create the normal server arrow.
            if(bowDrawTicks>=20 && bowCooldown==0) {
                c.player.releaseUsingItem();
                bowDrawTicks=0;bowCooldown=12;
            }
            return true;
        }
        if(bowCooldown==0) {
            c.player.gameMode.useItem(c.player,c.player.level(),c.player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
            if(!c.player.isUsingItem())c.player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            bowDrawTicks=1;
        }
        return true;
    }
    private Vec3 approach(LivingEntity target,double radius){return FormationPlanner.point(target.position(),formationIndex,formationCount,radius,routeVariant);}
    /**
     * Iterative lead calculation for a vanilla arrow. The small trajectory
     * simulator models the 0.99 per-tick drag and 0.05 gravity used by arrows,
     * while the outer loop accounts for target movement during the remaining
     * draw and flight time.
     */
    private Vec3 predictAim(LivingEntity target) {
        Vec3 origin=c.player.getEyePosition();
        Vec3 velocity=target.getDeltaMovement();
        double drawSeconds=Math.max(0,20-bowDrawTicks)/20.0;
        double flightSeconds=Math.max(0.05,origin.distanceTo(target.getEyePosition())/3.0);
        double launchPitch=0;
        Vec3 future=target.getEyePosition();
        for(int outer=0;outer<7;outer++) {
            future=target.getEyePosition().add(velocity.scale(drawSeconds+flightSeconds));
            double dx=future.x-origin.x,dz=future.z-origin.z;
            double horizontal=Math.sqrt(dx*dx+dz*dz);
            if(horizontal<0.05)return future;
            double desiredY=future.y-origin.y;
            double low=-0.45,high=1.05;
            for(int i=0;i<16;i++) {
                double mid=(low+high)*0.5;
                if(trajectory(mid,horizontal).height()<desiredY)low=mid;else high=mid;
            }
            launchPitch=(low+high)*0.5;
            flightSeconds=trajectory(launchPitch,horizontal).seconds();
        }
        double dx=future.x-origin.x,dz=future.z-origin.z;
        double horizontal=Math.sqrt(dx*dx+dz*dz);
        if(horizontal<0.05)return future;
        return new Vec3(origin.x+dx,origin.y+horizontal*Math.tan(launchPitch),origin.z+dz);
    }
    private record Trajectory(double seconds,double height){}
    private static Trajectory trajectory(double pitch,double horizontal) {
        double vx=3.0*Math.cos(pitch),vy=3.0*Math.sin(pitch),x=0,y=0;
        for(int tick=1;tick<=100;tick++) {
            x+=vx;y+=vy;
            if(x>=horizontal)return new Trajectory(tick/20.0,y);
            vx*=0.99;vy=vy*0.99-0.05;
        }
        return new Trajectory(5.0,y);
    }
    public void resetRanged(){rangedDuty=false;bowDrawTicks=0;bowCooldown=0;routeTarget=null;routeVariant=0;if(c.player.isUsingItem())c.player.releaseUsingItem();}
    public static boolean rangedAssignment(String name){return Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(),2)==0;}
}
