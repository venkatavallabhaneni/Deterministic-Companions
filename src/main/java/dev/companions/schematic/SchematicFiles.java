package dev.companions.schematic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Finds .schem files in the game instance's schematics folders: the
 * instance/server "schematics" directory (Litematica, Baritone and most tools),
 * the world's own "schematics" directory and WorldEdit's config directory.
 * Only plain file names are accepted, so commands cannot read outside them.
 */
public final class SchematicFiles {
    private SchematicFiles(){}
    public static List<Path> directories(MinecraftServer server){
        var root=server.getServerDirectory().toAbsolutePath().normalize();
        var out=new ArrayList<Path>();
        for(Path p:List.of(root.resolve("schematics"),server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("schematics"),root.resolve("config").resolve("worldedit").resolve("schematics")))
            if(!out.contains(p))out.add(p);
        return out;
    }
    public static List<String> list(MinecraftServer server){
        var names=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for(Path dir:directories(server)){
            if(!Files.isDirectory(dir))continue;
            try(Stream<Path> files=Files.list(dir)){
                files.filter(Files::isRegularFile).map(p->p.getFileName().toString()).filter(n->n.toLowerCase(Locale.ROOT).endsWith(".schem")).forEach(names::add);
            }catch(IOException ignored){}
        }
        return List.copyOf(names);
    }
    public static Path resolve(MinecraftServer server,String name){
        if(name.isBlank()||name.contains("/")||name.contains("\\")||name.contains(".."))throw new IllegalArgumentException("Use a schematic file name, not a path");
        String file=name.toLowerCase(Locale.ROOT).endsWith(".schem")?name:name+".schem";
        for(Path dir:directories(server)){
            Path candidate=dir.resolve(file).normalize();
            if(candidate.startsWith(dir)&&Files.isRegularFile(candidate))return candidate;
        }
        throw new IllegalArgumentException("No schematic named "+file+" in "+directories(server).getFirst()+" (see /bot schematic list)");
    }
}
