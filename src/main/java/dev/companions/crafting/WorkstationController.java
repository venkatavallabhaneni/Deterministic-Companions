package dev.companions.crafting;

import dev.companions.player.CompanionController;
import dev.companions.world.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.inventory.*;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Private owned workstations; never adopts a public or another companion's table. */
public final class WorkstationController {
    public enum Result { READY, MOVING, NEED_ITEM, BLOCKED }
    public record SavedTable(String dimension,int x,int y,int z){}
    private final CompanionController c;
    private BlockPos table,furnace,opened,pending;
    private Block pendingType;
    private String dimension;
    private final Set<BlockPos> rejected=new HashSet<>();
    private List<BlockPos> candidates=List.of();
    private int cursor,attemptTicks,preparationTicks;
    private String status="idle";
    public WorkstationController(CompanionController c){this.c=c;}
    public String status(){return status;}
    public SavedTable saveTable(){return table==null?null:new SavedTable(dimension,table.getX(),table.getY(),table.getZ());}
    public void restoreTable(SavedTable saved){
        if(saved==null||saved.dimension()==null)return;
        dimension=saved.dimension();table=new BlockPos(saved.x(),saved.y(),saved.z());
        if(!c.resources.stations.reserve(c.player.getUUID(),dimension,table))table=null;
    }
    public boolean ownsOpenTable(){return table!=null&&table.equals(opened)&&c.player.containerMenu instanceof CraftingMenu&&c.player.containerMenu.stillValid(c.player);}
    public void endSession(){
        c.player.closeContainer();c.player.stopUsingItem();c.player.setShiftKeyDown(false);opened=null;
        pending=null;pendingType=null;candidates=List.of();cursor=attemptTicks=preparationTicks=0;rejected.clear();
        if(table==null)c.resources.stations.release(c.player.getUUID());
    }
    public Result ensure(Block block) {
        String current=c.player.level().dimension().identifier().toString();
        if(!current.equals(dimension)){endSession();table=furnace=null;dimension=current;c.resources.stations.release(c.player.getUUID());}
        if(++preparationTicks>1200){status="private workstation preparation exceeded 60 seconds; check space and placement permissions";return Result.BLOCKED;}
        boolean crafting=block==Blocks.CRAFTING_TABLE;BlockPos station=crafting?table:furnace;
        if(station!=null&&(!c.player.level().hasChunkAt(station)||!c.player.level().getBlockState(station).is(block)||station.distSqr(c.player.blockPosition())>48*48)){
            endSession();if(crafting){table=null;c.resources.stations.release(c.player.getUUID());}else furnace=null;station=null;
        }
        if(station==null){
            String item=BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
            if(c.inventory.find(item)<0){status="crafting private "+item;return Result.NEED_ITEM;}
            if(pendingType!=block){pending=null;pendingType=block;candidates=sites(c.player.blockPosition());cursor=attemptTicks=0;rejected.clear();}
            if(pending==null){
                for(int budget=0;cursor<candidates.size()&&budget<96;budget++){
                    var p=candidates.get(cursor++);var cell=dev.companions.navigation.WorldNeighbors.cell(p);
                    if(rejected.contains(p)||!c.navigation.world.replaceable(cell)||!c.navigation.world.safe(cell)||!c.navigation.world.support(cell.add(0,-1,0)))continue;
                    if(crafting&&!c.resources.stations.reserve(c.player.getUUID(),dimension,p))continue;
                    pending=p;attemptTicks=0;break;
                }
                if(pending==null){status="searching for a safe private table site";return cursor<candidates.size()?Result.MOVING:Result.BLOCKED;}
            }
            status="placing private workstation at "+pending.toShortString();
            if(++attemptTicks>200||c.navigation.failed()){reject(crafting);return Result.MOVING;}
            if(c.actions.placeBlock(pending,item)==WorldActionController.Result.SUCCESS){station=pending;pending=null;c.navigation.stop();}
            else {
                if(attemptTicks%20==1&&!InteractionPosition.approach(c,pending))reject(crafting);
                return Result.MOVING;
            }
        }
        if(crafting)table=station;else furnace=station;
        if(!c.actions.reachable(station)){
            status="walking to own workstation at "+station.toShortString();
            if(c.navigation.failed())return Result.BLOCKED;
            return InteractionPosition.approach(c,station)?Result.MOVING:Result.BLOCKED;
        }
        c.navigation.pause();c.player.stopUsingItem();c.player.setShiftKeyDown(false);
        if(station.equals(opened)&&c.player.containerMenu.stillValid(c.player)
            &&(crafting?c.player.containerMenu instanceof CraftingMenu:c.player.containerMenu instanceof FurnaceMenu)){status="ready";preparationTicks=0;return Result.READY;}
        if(!c.actions.useBlock(station)){status="workstation interaction denied";return Result.BLOCKED;}
        opened=station;status="ready";preparationTicks=0;
        return (crafting?c.player.containerMenu instanceof CraftingMenu:c.player.containerMenu instanceof FurnaceMenu)?Result.READY:Result.BLOCKED;
    }
    private void reject(boolean crafting){
        rejected.add(pending);pending=null;c.navigation.stop();if(crafting)c.resources.stations.release(c.player.getUUID());
    }
    public static List<BlockPos> sites(BlockPos origin){
        var result=new ArrayList<BlockPos>();
        for(int r=2;r<=8;r++)for(int dy:new int[]{0,-1,1,-2,2})for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)
            if(Math.max(Math.abs(x),Math.abs(z))==r)result.add(origin.offset(x,dy,z));
        result.sort(Comparator.comparingDouble((BlockPos p)->p.distSqr(origin)).thenComparingLong(BlockPos::asLong));
        return List.copyOf(result);
    }
}
