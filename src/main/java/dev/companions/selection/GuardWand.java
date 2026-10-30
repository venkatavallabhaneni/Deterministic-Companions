package dev.companions.selection;

import dev.companions.player.*;
import dev.companions.task.AreaGuardTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

public final class GuardWand {
    public static final int MAX_SELECTIONS=16;
    private final MinecraftServer server;
    private final FakePlayerManager manager;
    private final Map<UUID,GuardSelection> selections=new HashMap<>();
    public GuardWand(MinecraftServer server,FakePlayerManager manager){this.server=server;this.manager=manager;}
    public static boolean isWand(ItemStack stack){return stack.is(Items.STICK)&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().contains("companions_guard_wand");}
    public void begin(ServerPlayer player,String targets){
        if(!selections.containsKey(player.getUUID())&&selections.size()>=MAX_SELECTIONS)throw new IllegalArgumentException("Maximum simultaneous wand selections reached: "+MAX_SELECTIONS);
        List<String> parsed=GuardSelection.parseNames(targets);
        var bots=parsed.equals(List.of("all"))?manager.all().stream().filter(c->c.player.level()==player.level()).toList():parsed.stream().map(manager::get).toList();
        if(bots.isEmpty())throw new IllegalArgumentException("Spawn companions in this dimension first");
        for(var c:bots)if(c.player.level()!=player.level())throw new IllegalArgumentException("Companion must be in your dimension: "+c.player.getPlainTextName());
        selections.put(player.getUUID(),new GuardSelection(bots.stream().map(c->c.player.getPlainTextName()).toList(),player.level().dimension().identifier().toString()));
        int slot=-1;for(int i=0;i<36;i++)if(isWand(player.getInventory().getItem(i))){slot=i;break;}
        if(slot<0){
            for(int i=0;i<36;i++)if(player.getInventory().getItem(i).isEmpty()){slot=i;break;}
            if(slot<0){slot=player.getInventory().getSelectedSlot();player.drop(player.getInventory().getItem(slot),false,false);}
            var stick=new ItemStack(Items.STICK);var tag=new CompoundTag();tag.putBoolean("companions_guard_wand",true);
            stick.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));stick.set(DataComponents.CUSTOM_NAME,Component.literal("Guard Area Wand"));player.getInventory().setItem(slot,stick);
        }
        if(slot<9)player.getInventory().setSelectedSlot(slot);else{int held=player.getInventory().getSelectedSlot();var old=player.getInventory().getItem(held);player.getInventory().setItem(held,player.getInventory().getItem(slot));player.getInventory().setItem(slot,old);}
        player.inventoryMenu.broadcastChanges();tell(player,"Guard wand: left-click corner 1, then right-click corner 2 to assign "+String.join(", ",bots.stream().map(c->c.player.getPlainTextName()).toList())+". All X/Y/Z coordinates are included.");
    }
    public void click(ServerPlayer player,BlockPos pos,boolean primary){
        try{
            var selection=selections.get(player.getUUID());if(selection==null)throw new IllegalArgumentException("Run /bot guardarea all or /bot guardarea bob,tracy first");
            selection.select(primary,new GuardSelection.Corner(pos.getX(),pos.getY(),pos.getZ()),player.level().dimension().identifier().toString());
            if(selection.applied())return;
            if(selection.complete()){
                var area=selection.preview();var bots=selection.names.stream().map(manager::get).toList();
                for(var c:bots)if(c.player.level()!=player.level())throw new IllegalArgumentException("A selected companion changed dimension; request a new wand selection");
                dev.companions.command.AreaGuardCommands.validateArea(player.level(),area);
                for(int i=0;i<bots.size();i++)bots.get(i).tasks.set(new AreaGuardTask(bots.get(i),area,i,bots.size()));
                for(var bot:bots)manager.store.save(bot);
                selection.markApplied();
                tell(player,"Guarding "+area.describe()+" with "+bots.size()+" companion(s): "+String.join(", ",selection.names));
            }else tell(player,"Corner 1: "+pos.toShortString()+". Right-click corner 2 to assign guards.");
            outline(player,selection);
        }catch(IllegalArgumentException|IllegalStateException e){tell(player,e.getMessage());}
    }
    public void tick(){
        if(server.getTickCount()%20!=0)return;
        selections.entrySet().removeIf(e->server.getPlayerList().getPlayer(e.getKey())==null);
        for(var entry:selections.entrySet()){
            var p=server.getPlayerList().getPlayer(entry.getKey());if(p!=null&&isWand(p.getMainHandItem()))outline(p,entry.getValue());
        }
    }
    private void outline(ServerPlayer p,GuardSelection selection){
        if(!selection.dimension.equals(p.level().dimension().identifier().toString())||selection.preview()==null)return;
        for(var v:SelectionOutline.points(selection.preview()))p.level().sendParticles(p,ParticleTypes.END_ROD,true,false,v.x(),v.y(),v.z(),1,0,0,0,0);
    }
    private static void tell(ServerPlayer p,String message){p.sendSystemMessage(Component.literal(message));}
}
