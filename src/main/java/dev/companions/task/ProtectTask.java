package dev.companions.task;

import dev.companions.combat.CombatController;
import dev.companions.combat.FormationPlanner;
import dev.companions.combat.ProgressWatchdog;
import dev.companions.navigation.WorldNeighbors;
import dev.companions.player.CompanionController;
import dev.companions.player.CompanionPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

public final class ProtectTask extends BaseTask {
    private final UUID protectedId;
    private LivingEntity threat;
    private int ticks, formationIndex=-1, formationCount=1, followVariant;
    private final ProgressWatchdog progress=new ProgressWatchdog(80);

    public ProtectTask(CompanionController c, ServerPlayer player) {
        super(c,"PROTECT "+player.getPlainTextName());
        protectedId=player.getUUID();
    }

    @Override public void start() {
        c.combat.passive=false;
        c.combat.protectedPlayer=protectedId;
        c.combat.rangedDuty=c.rangedDutyOverride!=null?c.rangedDutyOverride:CombatController.rangedAssignment(c.player.getPlainTextName());
        c.rangedDutyOverride=null;
        refreshFormation();
        c.formationIndexOverride=c.formationCountOverride=null;
        c.inventory.swordDuty=true;
        c.inventory.diamondSword();
        c.inventory.rangedSupplies();
    }

    @Override public void cancel() {super.cancel();c.combat.protectedPlayer=null;}

    @Override public void tick() {
        var player=c.player.level().getServer().getPlayerList().getPlayer(protectedId);
        if(player==null||!player.isAlive()||player.level()!=c.player.level()) {
            c.navigation.stop();
            status="PROTECT waiting for player in same dimension";
            return;
        }
        if(ticks%40==0)refreshFormation();
        if(++ticks%10==1 || threat!=null&&!threat.isAlive())threat=c.combat.threat(player,player.position(),20);

        if(threat!=null && !threat.getUUID().equals(protectedId) && threat.distanceToSqr(player)<400 && c.combat.engage(threat)) {
            boolean moving=!(c.combat.rangedDuty&&threat instanceof net.minecraft.world.entity.monster.RangedAttackMob
                &&c.player.distanceToSqr(threat)<=400&&c.player.hasLineOfSight(threat))
                &&(c.player.distanceToSqr(threat)>6.25||!c.player.hasLineOfSight(threat));
            boolean executing=moving&&c.navigation.followingPath()&&!c.navigation.searching();
            if(progress.tick(c.player.position(),executing)) {
                c.combat.recoverRoute();
                status="PROTECT "+player.getPlainTextName()+" - replanning blocked combat approach";
            } else status="PROTECT "+player.getPlainTextName()+" - engaging "+threat.getName().getString();
            return;
        }

        c.player.stopUsingItem();
        if(c.navigation.failed()){followVariant++;c.navigation.stop();}
        Post post=formationPost(player.position(),player.blockPosition());
        boolean moving=c.player.position().distanceToSqr(post.point())>post.tolerance()*post.tolerance();
        if(moving)c.navigation.targetTracking(post.point(),post.tolerance());

        // A bounded search is useful work and must not count as a movement
        // freeze. The watchdog only measures a route currently being executed.
        boolean executing=moving&&c.navigation.followingPath()&&!c.navigation.searching();
        if(progress.tick(c.player.position(),executing)) {
            followVariant++;
            c.navigation.stop();
            post=formationPost(player.position(),player.blockPosition());
            c.navigation.targetTracking(post.point(),post.tolerance());
            status="PROTECT "+player.getPlainTextName()+" - replanning blocked formation route";
            return;
        }
        if(moving)status="PROTECT "+player.getPlainTextName()+(c.navigation.searching()?" - calculating formation route":" - returning to formation");
        else {c.navigation.stop();status="PROTECT "+player.getPlainTextName()+" - watching";}
    }

    private record Post(Vec3 point,double tolerance) {}

    private Post formationPost(Vec3 center,BlockPos base) {
        for(int attempt=0;attempt<8;attempt++) {
            Vec3 point=FormationPlanner.point(center,formationIndex,formationCount,2.5,followVariant+attempt);
            BlockPos feet=BlockPos.containing(point.x,base.getY(),point.z);
            if(c.navigation.world.stand(WorldNeighbors.cell(feet)))return new Post(Vec3.atBottomCenterOf(feet),0.8);
        }
        // In a tight room, keep a broad protection radius. Sending every bot
        // to the player's exact block makes them collide and falsely stall.
        return new Post(center,4.0);
    }

    private void refreshFormation() {
        var companions=c.player.level().getServer().getPlayerList().getPlayers().stream()
            .filter(p->p instanceof CompanionPlayer)
            .sorted(Comparator.comparing(p->p.getPlainTextName().toLowerCase(Locale.ROOT))).toList();
        int next=Math.max(0,companions.indexOf(c.player)),count=Math.clamp(companions.size(),1,4);
        if(c.formationIndexOverride!=null)next=c.formationIndexOverride;
        if(c.formationCountOverride!=null)count=c.formationCountOverride;
        if(next!=formationIndex||count!=formationCount) {
            formationIndex=next;
            formationCount=count;
            followVariant=0;
            c.combat.formation(next,count);
            c.navigation.stop();
        }
    }
}
