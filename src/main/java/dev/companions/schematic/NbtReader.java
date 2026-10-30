package dev.companions.schematic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Minimal, bounded reader for the gzip NBT used by Sponge .schem files.
 * Pure Java so schematic parsing is testable without a Minecraft bootstrap and
 * independent of vanilla's changing CompoundTag accessor API. Compounds become
 * maps, lists become lists, byte arrays stay byte[], int arrays int[].
 */
public final class NbtReader {
    /** Hard limits so a hostile or corrupt file cannot exhaust server memory. */
    public static final long MAX_BYTES=64L*1024*1024;
    private static final int MAX_DEPTH=64;
    private final DataInputStream in;
    private long budget=MAX_BYTES;
    private NbtReader(InputStream in){this.in=new DataInputStream(new BufferedInputStream(in));}
    public static Map<String,Object> readCompressed(InputStream raw) throws IOException {
        var buffered=new BufferedInputStream(raw);buffered.mark(2);
        int a=buffered.read(),b=buffered.read();buffered.reset();
        // Sponge files are gzip; tolerate an uncompressed NBT file as well.
        InputStream source=a==0x1f&&b==0x8b?new GZIPInputStream(buffered):buffered;
        var reader=new NbtReader(source);
        int type=reader.in.readUnsignedByte();
        if(type!=10)throw new IOException("Root tag is not a compound");
        reader.string();
        @SuppressWarnings("unchecked") var root=(Map<String,Object>)reader.payload(10,0);
        return root;
    }
    private void spend(long n) throws IOException {budget-=n;if(budget<0)throw new IOException("Schematic NBT exceeds "+MAX_BYTES+" bytes");}
    private String string() throws IOException {int n=in.readUnsignedShort();spend(n);byte[] b=in.readNBytes(n);if(b.length<n)throw new EOFException();return new String(b,StandardCharsets.UTF_8);}
    private int length() throws IOException {int n=in.readInt();if(n<0)throw new IOException("Negative NBT array length");return n;}
    private Object payload(int type,int depth) throws IOException {
        if(depth>MAX_DEPTH)throw new IOException("NBT nesting too deep");
        switch(type){
            case 1:spend(1);return in.readByte();
            case 2:spend(2);return in.readShort();
            case 3:spend(4);return in.readInt();
            case 4:spend(8);return in.readLong();
            case 5:spend(4);return in.readFloat();
            case 6:spend(8);return in.readDouble();
            case 7:{int n=length();spend(n);byte[] b=in.readNBytes(n);if(b.length<n)throw new EOFException();return b;}
            case 8:return string();
            case 9:{
                int element=in.readUnsignedByte(),n=length();spend(n);
                var list=new ArrayList<Object>(Math.min(n,1024));
                for(int i=0;i<n;i++)list.add(payload(element,depth+1));
                return list;
            }
            case 10:{
                var map=new LinkedHashMap<String,Object>();
                for(int child;(child=in.readUnsignedByte())!=0;)map.put(string(),payload(child,depth+1));
                return map;
            }
            case 11:{int n=length();spend(4L*n);int[] v=new int[n];for(int i=0;i<n;i++)v[i]=in.readInt();return v;}
            case 12:{int n=length();spend(8L*n);long[] v=new long[n];for(int i=0;i<n;i++)v[i]=in.readLong();return v;}
            default:throw new IOException("Unknown NBT tag type "+type);
        }
    }
}
