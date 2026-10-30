package dev.companions.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FakePlayerManager {
    private final MinecraftServer server;
    private final Map<String,CompanionController> active=new LinkedHashMap<>();
    public final dev.companions.world.ResourceClaims resources=new dev.companions.world.ResourceClaims();
    public final dev.companions.selection.GuardWand wand;
    public final dev.companions.persistence.CompanionStore store;
    /** The schematic currently (or most recently) built by the companions. */
    public dev.companions.schematic.SchematicJob schematic;
    public FakePlayerManager(MinecraftServer server) { this.server=server;store=new dev.companions.persistence.CompanionStore(server);wand=new dev.companions.selection.GuardWand(server,this); }
    public Collection<CompanionController> all() { return List.copyOf(active.values()); }
    public CompanionController get(String name) {
        CompanionController c=active.get(name.toLowerCase(Locale.ROOT));
        if (c==null) throw new IllegalArgumentException("No active companion: "+name);
        return c;
    }
    public CompanionController spawn(String name, ServerPlayer owner) {return spawn(name,owner,false);}
    public CompanionController spawn(String name, ServerPlayer owner,boolean flyer) {
        if(name.equalsIgnoreCase("all"))throw new IllegalArgumentException("The name 'all' is reserved for group commands");
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Use 1–16 letters, digits or underscores");
        if(flyer)CompanionLimit.checkFlyer((int)active.values().stream().filter(c->c.flyer).count());
        else CompanionLimit.check((int)active.values().stream().filter(c->!c.flyer).count());
        if (server.getPlayerList().getPlayerByName(name)!=null) throw new IllegalArgumentException("Name is already online");
        String key=name.toLowerCase(Locale.ROOT);
        UUID id=UUID.nameUUIDFromBytes(("deterministic-companions:"+key).getBytes(StandardCharsets.UTF_8));
        var saved=store.load(id);
        if(saved.isPresent()&&saved.get().flyer()!=flyer)throw new IllegalArgumentException("Saved companion type differs; respawn using its original type");
        CompanionPlayer p=new CompanionPlayer(server,owner.level(),new GameProfile(id,name));
        server.getPlayerList().placeNewPlayer(new LocalConnection(),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));
        server.getPlayerList().loadPlayerData(p.nameAndId()).ifPresent(tag -> p.load(TagValueInput.create(ProblemReporter.DISCARDING,p.registryAccess(),tag)));
        p.teleportTo(owner.level(),owner.getX()+1,owner.getY(),owner.getZ(),Set.of(),owner.getYRot(),0,true);
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.hasChangedDimension();
        if(p.getHealth()<=0){p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);}
        CompanionController c=new CompanionController(p,saved.map(dev.companions.persistence.CompanionStore.Record::owner).orElse(owner.getUUID()),resources);
        c.flyer=flyer;
        saved.ifPresent(r -> store.apply(r,c));
        c.health(c.config.maxHealth,false);
        if(!flyer)c.inventory.diamondSword();
        else c.tasks.set(new dev.companions.task.FlyingHunterTask(c));
        active.put(key,c);
        store.save(c);
        return c;
    }
    public void remove(String name) {
        CompanionController c=get(name); c.tasks.stop();
        store.save(c);
        server.getPlayerList().remove(c.player); // Vanilla atomically saves playerdata, including inventory.
        resources.stations.release(c.player.getUUID());
        active.remove(name.toLowerCase(Locale.ROOT));
    }
    public void tick() {
        wand.tick();
        for (CompanionController c:all()) {
            if (!c.player.isAlive() || c.player.isRemoved()) { remove(c.player.getPlainTextName()); continue; }
            try {c.tick();if(server.getTickCount()%1200==0)store.save(c);}
            catch(RuntimeException e){c.tasks.stop();org.slf4j.LoggerFactory.getLogger("companions").error("Stopped companion {} after controller failure",c.player.getPlainTextName(),e);}
        }
    }
    public void close() { for (CompanionController c:all()) remove(c.player.getPlainTextName()); }
}
