package dev.companions.task;

import dev.companions.player.CompanionController;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public final class PlayerTrackingTarget implements TrackingTarget {
    private final CompanionController c;
    private final UUID id;
    private final String name;
    public PlayerTrackingTarget(CompanionController c,ServerPlayer p) {this.c=c;id=p.getUUID();name=p.getPlainTextName();}
    private ServerPlayer player() {return c.player.level().getServer().getPlayerList().getPlayer(id);}
    @Override public boolean available() {var p=player();return p!=null && p.isAlive() && p.level()==c.player.level();}
    @Override public boolean nearby() {return available() && c.player.distanceToSqr(player())<=6.25;}
    @Override public void approach() {if(available()) c.navigation.targetTracking(player().position(),2);}
    @Override public void halt() {c.navigation.stop();}
    @Override public boolean blocked() {return c.navigation.failed();}
    @Override public String name() {return name;}
}
