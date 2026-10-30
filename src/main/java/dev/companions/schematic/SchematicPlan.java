package dev.companions.schematic;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** World-placed, rotated block list for one schematic. Built off-thread; registries are read-only. */
public record SchematicPlan(String name,BlockPos min,BlockPos max,List<Placement> placements,Map<Item,Integer> materials,Map<String,Integer> skipped) {
    public static final int MAX_BLOCKS=131072;
    /** partner is the automatically created second cell (door top, bed head), or null. */
    public record Placement(BlockPos pos,BlockState state,Item item,int count,int stage,BlockPos partner) {
        public boolean fitting(){return stage==PlacementRules.FITTINGS;}
        public boolean furnishing(){return stage==PlacementRules.FURNISHINGS;}
    }

    public static SchematicPlan create(Schematic schematic,BlockPos origin,Rotation rotation) {
        int w=schematic.width(),h=schematic.height(),l=schematic.length();
        boolean swap=rotation==Rotation.CLOCKWISE_90||rotation==Rotation.COUNTERCLOCKWISE_90;
        BlockPos max=origin.offset((swap?l:w)-1,h-1,(swap?w:l)-1);
        var states=new BlockState[schematic.palette().size()];
        var skipped=new TreeMap<String,Integer>();
        for(int i=0;i<states.length;i++){
            String id=schematic.palette().get(i);
            if(Schematic.air(id))continue;
            try{states[i]=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,id,false).blockState().rotate(rotation);}
            catch(Exception e){
                // Older schematics may carry properties a newer block no longer has; keep the block itself.
                try{states[i]=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,id.replaceAll("\\[.*$",""),false).blockState().rotate(rotation);}
                catch(Exception ignored){states[i]=null;}
            }
        }
        var placements=new ArrayList<Placement>();
        var materials=new LinkedHashMap<Item,Integer>();
        for(int y=0;y<h;y++)for(int z=0;z<l;z++)for(int x=0;x<w;x++){
            String id=schematic.state(x,y,z);
            if(Schematic.air(id))continue;
            BlockState state=states[schematic.blocks()[(y*l+z)*w+x]];
            if(state==null){skipped.merge(id.replaceAll("\\[.*$","")+" (unknown in this version)",1,Integer::sum);continue;}
            if(state.isAir())continue;
            String reason=PlacementRules.unsupported(state);
            String block=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if(reason!=null){skipped.merge(block+" ("+reason+")",1,Integer::sum);continue;}
            if(PlacementRules.secondPart(state))continue;
            BlockPos pos=origin.offset(rotate(x,z,w,l,rotation,true),y,rotate(x,z,w,l,rotation,false));
            var direction=PlacementRules.partnerDirection(state);
            Item item=PlacementRules.item(state);int count=PlacementRules.itemCount(state);
            placements.add(new Placement(pos,state,item,count,PlacementRules.stage(state),direction==null?null:pos.relative(direction)));
            materials.merge(item,count,Integer::sum);
            if(placements.size()>MAX_BLOCKS)throw new IllegalArgumentException("Schematic has more than "+MAX_BLOCKS+" blocks");
        }
        return new SchematicPlan(schematic.name(),origin,max,placements,materials,skipped);
    }
    /** Clockwise rotation about the footprint, keeping the minimum corner at the origin. */
    static int rotate(int x,int z,int w,int l,Rotation r,boolean xAxis){
        return switch(r){
            case NONE->xAxis?x:z;
            case CLOCKWISE_90->xAxis?l-1-z:x;
            case CLOCKWISE_180->xAxis?w-1-x:l-1-z;
            case COUNTERCLOCKWISE_90->xAxis?z:w-1-x;
        };
    }
    /** Inside the footprint's columns, at any height. */
    public boolean overFootprint(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    public boolean contains(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
}
