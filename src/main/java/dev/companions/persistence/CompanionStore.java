package dev.companions.persistence;

import com.google.gson.*;
import dev.companions.config.CompanionConfig;
import dev.companions.player.CompanionController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Only mod-owned metadata; vanilla PlayerList owns inventory/health/hunger NBT. */
public final class CompanionStore {
    public record Record(int version,UUID identity,UUID owner,String name,int homeX,int homeY,int homeZ,String dimension,CompanionConfig config,boolean passive,boolean guardDirtIssued,dev.companions.crafting.WorkstationController.SavedTable table,boolean flyer) {}
    private final Path directory;
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    public CompanionStore(MinecraftServer server) {directory=server.getWorldPath(LevelResource.ROOT).resolve("deterministic_companions");}
    private Path path(UUID id){return directory.resolve(id+".json");}
    public Optional<Record> load(UUID id) {
        Path file=path(id);if(!Files.exists(file))return Optional.empty();
        try(var reader=Files.newBufferedReader(file)) {
            Record r=gson.fromJson(reader,Record.class);
            if(r==null||r.version!=1||!id.equals(r.identity)||r.owner==null||r.dimension==null||r.config==null)throw new IOException("Invalid companion metadata");
            r.config.maxDistance=Math.max(4,Math.min(1024,r.config.maxDistance));
            r.config.maxHealth=dev.companions.config.HealthPolicy.restore(r.config.maxHealth);
            return Optional.of(r);
        }catch(IOException|JsonParseException e){throw new IllegalStateException("Cannot read saved companion; existing data retained: "+file,e);}
    }
    public void apply(Record r,CompanionController c) {c.home=new BlockPos(r.homeX,r.homeY,r.homeZ);c.homeDimension=r.dimension;c.config=r.config;c.combat.passive=r.passive;c.guardDirtIssued=r.guardDirtIssued;c.workstations.restoreTable(r.table);c.flyer=r.flyer;}
    public void save(CompanionController c) {
        var p=c.player;Record r=new Record(1,p.getUUID(),c.owner,p.getPlainTextName(),c.home.getX(),c.home.getY(),c.home.getZ(),c.homeDimension,c.config,c.combat.passive,c.guardDirtIssued,c.workstations.saveTable(),c.flyer);
        try {
            Files.createDirectories(directory);Path file=path(p.getUUID()),temp=Files.createTempFile(directory,p.getUUID().toString(),".tmp");
            try {
                Files.writeString(temp,gson.toJson(r));
                if(Files.exists(file))Files.copy(file,directory.resolve(p.getUUID()+".json.bak"),StandardCopyOption.REPLACE_EXISTING);
                try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(temp);}
        }catch(IOException e){throw new IllegalStateException("Could not save companion metadata; playerdata remains managed by Minecraft",e);}
    }
}
