package dev.companions.task;

import dev.companions.inventory.FlyingLoadout;
import dev.companions.navigation.flight.FlightAirspace;
import dev.companions.navigation.flight.FlightPathfinder;
import dev.companions.navigation.flight.MaceApproach;
import dev.companions.player.CompanionController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** One primary task controls flight, target selection and vanilla mace attacks. */
public final class FlyingHunterTask extends BaseTask {
    private final FlyingLoadout loadout;
    private final FlightAirspace air;
    private final FlightPathfinder flightPath;
    private final UUID owner;
    private LivingEntity target;
    private UUID manualTarget;
    private int manualUntil,patrolIndex=-1,lastPatrolAdvance;
    private int ticks,lastRocket=-100,diveStart,stagnantTicks;
    private boolean diving;
    private Vec3 lastProgress;
    private Vec3 diveLanding,landingTargetPosition;
    private UUID landingTargetId;
    private List<Vec3> landingChoices=List.of();
    private int landingIndex,lastLandingCheck,lastLandingRetry;
    public FlyingHunterTask(CompanionController c){
        super(c,"FLYING patrol");loadout=c.flyingLoadout;owner=c.owner;
        air=new FlightAirspace(c);flightPath=new FlightPathfinder(air);
    }
    @Override public void start(){c.combat.passive=false;c.navigation.stop();loadout.maintain();loadout.holdMace();}
    @Override public void tick(){
        ticks++;
        if(ticks%20==1)loadout.maintain();
        if(manualTarget!=null && (target==null||!target.isAlive()||ticks>=manualUntil||c.player.distanceToSqr(target)>4096)){
            manualTarget=null;target=null;c.combat.explicitMobTarget(null);
        }
        if(manualTarget==null && target!=null && !visibleOutdoorTarget(target))target=null;
        if(target==null){diving=false;landingChoices=List.of();landingTargetId=null;landingTargetPosition=null;}
        if(manualTarget==null&&(ticks%10==1||target==null||!target.isAlive()||c.player.distanceToSqr(target)>900))target=findTarget();
        loadout.holdMace();
        if(target!=null){attack();return;}
        ServerPlayer leader=c.player.level().getServer().getPlayerList().getPlayer(owner);
        if(leader==null||leader.level()!=c.player.level()){c.actions.halt();status="FLYING waiting for owner";return;}
        if(patrolIndex<0)patrolIndex=PatrolOrbit.nearest(leader.position(),c.player.position());
        Vec3 post=PatrolOrbit.point(leader.position(),patrolIndex);
        if(c.player.position().distanceToSqr(post)<12.25&&ticks-lastPatrolAdvance>15){
            patrolIndex=(patrolIndex+1)%PatrolOrbit.POSTS;
            lastPatrolAdvance=ticks;
            post=PatrolOrbit.point(leader.position(),patrolIndex);
        }
        if(!cruise(post))return;
        status="FLYING patrolling around "+leader.getPlainTextName();
    }
    public void designate(LivingEntity mob){
        if(!(mob instanceof Mob)||!c.combat.allowedExplicitMob(mob)||mob.level()!=c.player.level())
            throw new IllegalArgumentException("Target must be a living mob in the flyer's dimension");
        c.combat.explicitMobTarget(mob.getUUID());
        manualTarget=mob.getUUID();target=mob;manualUntil=ticks+1200;
        flightPath.reset();landingChoices=List.of();landingTargetId=null;landingTargetPosition=null;
    }
    private boolean visibleOutdoorTarget(LivingEntity candidate){
        BlockPos eye=BlockPos.containing(candidate.getEyePosition());
        return c.player.level().hasChunkAt(eye) && c.player.level().canSeeSky(eye)
            && c.player.hasLineOfSight(candidate);
    }
    private LivingEntity findTarget(){
        return c.player.level().getEntitiesOfClass(LivingEntity.class,c.player.getBoundingBox().inflate(30),
            e->e.getType().getCategory()==MobCategory.MONSTER&&e.isAlive()&&c.combat.allowed(e)
                &&c.player.distanceToSqr(e)<=900&&visibleOutdoorTarget(e)).stream()
            .min(Comparator.comparingDouble((LivingEntity e)->c.player.distanceToSqr(e)).thenComparingInt(e->e.getId())).orElse(null);
    }
    private void attack(){
        Vec3 p=c.player.position(),enemy=target.position();
        if(diving){
            if(c.player.onGround()||ticks-diveStart>20||c.player.distanceToSqr(target)>25){diving=false;}
            else{
                smoothLook(target.getEyePosition());
                var velocity=c.player.getDeltaMovement();
                c.player.setDeltaMovement(FlightSteering.diveVelocity(c.player.position(),diveLanding,velocity.y));
                if(c.player.fallDistance>1&&c.player.distanceToSqr(target)<16
                    &&c.player.getAttackStrengthScale(0)>=1&&c.player.hasLineOfSight(target)
                    &&c.player.isWithinEntityInteractionRange(target,0)){
                    c.actions.attack(target);diving=false;
                    status="FLYING mace strike on "+target.getName().getString();return;
                }
                status="FLYING mace dive on "+target.getName().getString();return;
            }
        }
        if(c.player.isWithinEntityInteractionRange(target,0)&&c.player.hasLineOfSight(target)
            &&(c.player.onGround()||c.player.getAttackStrengthScale(0)>=1)){
            if(c.player.getAttackStrengthScale(0)>=1)c.actions.attack(target);
            status="FLYING mace melee on "+target.getName().getString();return;
        }
        Vec3 landing=chooseLanding();
        Vec3 divePoint=landing==null?enemy:landing;
        double horizontal=(p.x-divePoint.x)*(p.x-divePoint.x)+(p.z-divePoint.z)*(p.z-divePoint.z);
        if(horizontal<9&&p.y-divePoint.y>2.8&&p.y-divePoint.y<11
            &&(landing!=null||safeLanding(enemy))&&safeDive(divePoint)){
            c.navigation.stop();
            flightPath.reset();
            c.player.stopFallFlying();
            smoothLook(target.getEyePosition());
            c.player.setDeltaMovement(FlightSteering.diveVelocity(p,divePoint,-0.7));
            diveLanding=divePoint;
            diving=true;diveStart=ticks;
            status="FLYING mace dive on "+target.getName().getString();return;
        }
        Vec3 above=landing==null?enemy.add(0,2,0):landing.add(0,4,0);
        if(!cruise(above))return;
        status="FLYING hunting "+target.getName().getString();
    }
    private Vec3 chooseLanding(){
        Vec3 enemy=target.position();
        boolean changedTarget=!target.getUUID().equals(landingTargetId);
        if(changedTarget||landingTargetPosition==null||enemy.distanceToSqr(landingTargetPosition)>1||ticks-lastLandingCheck>=20){
            Vec3 previous=changedTarget||landingChoices.isEmpty()?null:landingChoices.get(landingIndex);
            landingChoices=MaceApproach.candidates(enemy,c.player.position(),
                point->air.landable(point)&&visibleFrom(point,target));
            landingIndex=0;
            if(previous!=null&&!landingChoices.isEmpty()){
                for(int i=0;i<landingChoices.size();i++)if(landingChoices.get(i).distanceToSqr(previous)<1.5){landingIndex=i;break;}
            }
            if(previous==null||landingChoices.isEmpty()||landingChoices.get(landingIndex).distanceToSqr(previous)>1.5)
                flightPath.reset();
            landingTargetId=target.getUUID();landingTargetPosition=enemy;lastLandingCheck=ticks;
        }
        if(landingChoices.isEmpty())return null;
        if(flightPath.failed()&&ticks-lastLandingRetry>=20){
            landingIndex=(landingIndex+1)%landingChoices.size();
            lastLandingRetry=ticks;flightPath.reset();
        }
        return landingChoices.get(landingIndex);
    }
    private boolean visibleFrom(Vec3 feet,LivingEntity mob){
        var hit=c.player.level().clip(new ClipContext(feet.add(0,1.6,0),mob.getEyePosition(),
            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.of(c.player)));
        return hit.getType()==HitResult.Type.MISS;
    }
    private boolean cruise(Vec3 destination){
        var p=c.player;
        if(p.isInWater()){
            c.navigation.targetTracking(p.position().add(0,4,0),2);
            status="FLYING swimming to open air";return false;
        }
        if(p.onGround()&&!c.player.level().noBlockCollision(p,p.getBoundingBox().move(0,1,0))){
            status="FLYING seeking open air";return false;
        }
        c.navigation.stop();
        if(p.onGround()){c.actions.jump();return true;}
        if(!p.isFallFlying())p.tryToStartFallFlying();
        destination=flightPath.liftGoal(destination);
        if(impactAhead()){
            var velocity=p.getDeltaMovement();
            p.setDeltaMovement(velocity.x*0.4,velocity.y*0.4,velocity.z*0.4);
            flightPath.reset();
            smoothLook(p.position().add(0,6,0));
            status="FLYING avoiding terrain";return false;
        }
        Vec3 course=flightPath.next(p.position(),destination,18);
        if(course==null){
            if(air.clear(p.position().add(0,4,0))){
                smoothLook(p.position().add(0,6,0));
                if(p.isFallFlying()&&ticks-lastRocket>=35
                    &&(p.getDeltaMovement().lengthSqr()<0.20||p.getY()<destination.y-5)
                    &&loadout.firework())lastRocket=ticks;
            }else c.actions.halt();
            if(flightPath.failed()&&ticks%40==0)flightPath.reset();
            status=flightPath.failed()?"FLYING route blocked":"FLYING planning air route";return false;
        }
        if(lastProgress==null||p.position().distanceToSqr(lastProgress)>4){lastProgress=p.position();stagnantTicks=0;}
        else if(++stagnantTicks>50){flightPath.reset();stagnantTicks=0;lastProgress=p.position();}
        smoothLook(course);
        double distance=p.position().distanceToSqr(destination);
        if(p.isFallFlying()&&ticks-lastRocket>=35&&
            (p.getDeltaMovement().lengthSqr()<0.20&&distance>36||p.getY()<destination.y-4&&distance>25)){
            if(loadout.firework())lastRocket=ticks;
        }
        return true;
    }
    private boolean impactAhead(){
        Vec3 velocity=c.player.getDeltaMovement();
        if(velocity.lengthSqr()<0.04)return false;
        Vec3 position=c.player.position();
        for(int tick=1;tick<=4;tick++)if(!air.clear(position.add(velocity.scale(tick))))return true;
        return false;
    }
    private void smoothLook(Vec3 destination){
        var player=c.player;
        var next=FlightSteering.step(new FlightSteering.Heading(player.getYRot(),player.getXRot()),
            FlightSteering.toward(player.getEyePosition(),destination));
        player.setYRot(next.yaw());player.setYHeadRot(next.yaw());player.setXRot(next.pitch());
    }
    private boolean safeLanding(Vec3 enemy){
        var world=c.navigation.world;
        var base=dev.companions.navigation.WorldNeighbors.cell(BlockPos.containing(enemy));
        for(int i=0;i<=4;i++){
            var cell=base.add(0,-i,0);
            if(world.hazard(cell))return false;
            if(world.stand(cell))return true;
        }
        return false;
    }
    private boolean safeDive(Vec3 enemy){
        Vec3 current=c.player.position();
        Vec3 landing=new Vec3(enemy.x,enemy.y+1.2,enemy.z);
        Vec3 delta=landing.subtract(current);
        int samples=Math.max(1,(int)Math.ceil(delta.length()*2));
        for(int i=1;i<=samples;i++){
            Vec3 step=delta.scale(i/(double)samples);
            if(!air.clear(current.add(step)))return false;
        }
        return true;
    }
    @Override public void cancel(){super.cancel();diving=false;flightPath.reset();c.combat.explicitMobTarget(null);c.actions.halt();}
}
