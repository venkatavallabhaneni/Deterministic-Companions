package dev.companions.schematic;

/** Splits a footprint into equal strips along its longer axis, one preferred strip per builder. */
public final class WorkSplit {
    private WorkSplit(){}
    public static int sector(int coord,int min,int max,int builders){
        if(builders<=1||max<=min)return 0;
        int span=max-min+1;
        int offset=Math.clamp(coord-min,0,span-1);
        return Math.min(builders-1,offset*builders/span);
    }
}
