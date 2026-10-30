package dev.companions.schematic;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Sponge schematic (versions 1–3) block grid. Entities and block entity data are not built. */
public record Schematic(String name,int width,int height,int length,List<String> palette,int[] blocks) {
    /** Volume cap keeps planning and per-tick claim scans bounded on the server thread. */
    public static final int MAX_VOLUME=256*256*256;
    public Schematic {
        palette=List.copyOf(palette);
        if(width<1||height<1||length<1)throw new IllegalArgumentException("Schematic has an empty size");
        if((long)width*height*length>MAX_VOLUME)throw new IllegalArgumentException("Schematic volume exceeds "+MAX_VOLUME+" blocks");
        if(blocks.length!=width*height*length)throw new IllegalArgumentException("Schematic block data does not match its size");
        for(int b:blocks)if(b<0||b>=palette.size())throw new IllegalArgumentException("Schematic block data references a missing palette entry");
    }
    public String state(int x,int y,int z){return palette.get(blocks[(y*length+z)*width+x]);}
    public static boolean air(String state){return state.equals("minecraft:air")||state.equals("minecraft:cave_air")||state.equals("minecraft:void_air")||state.equals("minecraft:structure_void");}
    public int solidCount(){int n=0;for(int b:blocks)if(!air(palette.get(b)))n++;return n;}

    public static Schematic read(Path file) throws IOException {
        if(Files.size(file)>NbtReader.MAX_BYTES)throw new IOException("Schematic file is too large");
        try(var in=Files.newInputStream(file)){
            String name=file.getFileName().toString();
            return parse(name.endsWith(".schem")?name.substring(0,name.length()-6):name,NbtReader.readCompressed(in));
        }
    }
    @SuppressWarnings("unchecked")
    public static Schematic parse(String name,Map<String,Object> root) throws IOException {
        // Version 3 wraps the payload in a "Schematic" compound and moves blocks into "Blocks".
        if(root.get("Schematic") instanceof Map<?,?> inner)root=(Map<String,Object>)inner;
        int width=dimension(root,"Width"),height=dimension(root,"Height"),length=dimension(root,"Length");
        Map<String,Object> container=root.get("Blocks") instanceof Map<?,?> blocks?(Map<String,Object>)blocks:root;
        Object paletteTag=container.get("Palette");
        Object dataTag=container.containsKey("Data")?container.get("Data"):container.get("BlockData");
        if(!(paletteTag instanceof Map<?,?> paletteMap))throw new IOException("Schematic has no block palette (only Sponge .schem is supported)");
        if(!(dataTag instanceof byte[] data))throw new IOException("Schematic has no block data");
        int size=paletteMap.size();
        String[] palette=new String[size];
        for(var entry:paletteMap.entrySet()){
            if(!(entry.getValue() instanceof Integer index)||index<0||index>=size||palette[index]!=null)throw new IOException("Schematic palette indexes are not contiguous");
            palette[index]=normalize((String)entry.getKey());
        }
        long volume=(long)width*height*length;
        if(volume>MAX_VOLUME)throw new IOException("Schematic volume exceeds "+MAX_VOLUME+" blocks");
        int[] blocks=new int[(int)volume];
        int cursor=0;
        for(int i=0;i<blocks.length;i++){
            int value=0,shift=0;
            while(true){
                if(cursor>=data.length)throw new IOException("Schematic block data is truncated");
                int b=data[cursor++];
                value|=(b&0x7f)<<shift;
                if((b&0x80)==0)break;
                shift+=7;if(shift>28)throw new IOException("Schematic block data has an invalid varint");
            }
            blocks[i]=value;
        }
        try{return new Schematic(name,width,height,length,Arrays.asList(palette),blocks);}
        catch(IllegalArgumentException e){throw new IOException(e.getMessage());}
    }
    private static int dimension(Map<String,Object> root,String key) throws IOException {
        if(!(root.get(key) instanceof Number n))throw new IOException("Schematic has no "+key);
        return Short.toUnsignedInt(n.shortValue());
    }
    private static String normalize(String state){return state.contains(":")?state:"minecraft:"+state;}
}
