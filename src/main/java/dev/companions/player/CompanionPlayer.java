package dev.companions.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;

public final class CompanionPlayer extends ServerPlayer {
    public CompanionPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile, ClientInformation.createDefault());
    }
    @Override public void tick() {
        var before=position();
        super.tick();
        // Real clients cause doTick through their listener. This local player has no client packets.
        doTick();
        var delta=position().subtract(before);
        checkMovementStatistics(delta.x,delta.y,delta.z);
        if (connection != null) connection.resetPosition();
        level().getChunkSource().move(this);
    }
    @Override public String getIpAddress() { return "127.0.0.1"; }
    @Override public boolean isClientAuthoritative() { return false; }
}
