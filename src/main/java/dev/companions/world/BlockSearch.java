package dev.companions.world;

import dev.companions.player.CompanionController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Predicate;

/** Incremental loaded-section scan, nearest sections first. No chunk generation or worker reads. */
public final class BlockSearch {
    public static final int RADIUS=150, BLOCKS_PER_TICK=1024, SECTIONS_PER_TICK=8;
    private final CompanionController c;
    private final BlockPos origin;
    private final Predicate<BlockState> match;
    private final Predicate<BlockPos> allowed;
    private final List<BlockPos> sections=new ArrayList<>();
    private final List<BlockPos> found=new ArrayList<>();
    private int sectionIndex,cellIndex;
    private boolean inspecting;
    public BlockSearch(CompanionController c,BlockPos origin,Predicate<BlockState> match,Predicate<BlockPos> allowed) {
        this.c=c;this.origin=origin.immutable();this.match=match;this.allowed=allowed;
        for(int x=(origin.getX()-RADIUS)>>4;x<=(origin.getX()+RADIUS)>>4;x++)
            for(int z=(origin.getZ()-RADIUS)>>4;z<=(origin.getZ()+RADIUS)>>4;z++)
                for(int y=(origin.getY()-RADIUS)>>4;y<=(origin.getY()+RADIUS)>>4;y++) {
                    BlockPos p=new BlockPos(x*16,y*16,z*16);
                    if(!c.player.level().isOutsideBuildHeight(p))sections.add(p);
                }
        sections.sort(Comparator.comparingDouble((BlockPos p)->p.offset(8,8,8).distSqr(origin)).thenComparingLong(BlockPos::asLong));
    }
    public static boolean inRange(BlockPos origin,BlockPos target) {
        return Math.abs((long)origin.getX()-target.getX())<=RADIUS && Math.abs((long)origin.getY()-target.getY())<=RADIUS && Math.abs((long)origin.getZ()-target.getZ())<=RADIUS;
    }
    public boolean done(){return sectionIndex>=sections.size()||!inspecting&&!found.isEmpty();}
    public List<BlockPos> results(){return List.copyOf(found);}
    public String status(){return "scanning XYZ +/-"+RADIUS+": sections "+sectionIndex+"/"+sections.size();}
    public void tick() {
        int blocks=0,checks=0;
        while(sectionIndex<sections.size() && blocks<BLOCKS_PER_TICK && checks<SECTIONS_PER_TICK) {
            BlockPos base=sections.get(sectionIndex);
            if(!c.player.level().hasChunkAt(base)){next();checks++;continue;}
            var chunk=c.player.level().getChunkAt(base);
            var section=chunk.getSection(chunk.getSectionIndex(base.getY()));
            if(!inspecting){checks++;if(!section.maybeHas(match)){next();continue;}inspecting=true;}
            while(cellIndex<4096 && blocks++<BLOCKS_PER_TICK) {
                int i=cellIndex++;BlockPos p=base.offset(i&15,(i>>8)&15,(i>>4)&15);
                if(inRange(origin,p) && match.test(section.getBlockState(i&15,(i>>8)&15,(i>>4)&15)) && allowed.test(p)) {
                    found.add(p);found.sort(Comparator.comparingDouble((BlockPos q)->q.distSqr(origin)).thenComparingLong(BlockPos::asLong));
                    if(found.size()>32)found.removeLast();
                }
            }
            if(cellIndex==4096){next();if(!found.isEmpty())return;}
        }
    }
    private void next(){sectionIndex++;cellIndex=0;inspecting=false;}
}
