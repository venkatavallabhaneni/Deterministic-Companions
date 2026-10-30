package dev.companions.ui;

import dev.companions.CompanionsMod;
import dev.companions.player.CompanionController;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Vanilla six-row chest protocol: server-only controls with no transferable icons. */
public final class BotControlMenu extends ChestMenu {
    private final ServerPlayer viewer;
    private final Map<Integer,Runnable> buttons=new HashMap<>();
    private String selected="all", page="Actions", message="Select a bot or All, then click an action.";
    private int amount=32,size=10;
    private String targetPlayer;
    private int playerPage;
    private long refreshAt,clickAt;

    public static void open(ServerPlayer viewer) {
        viewer.openMenu(new SimpleMenuProvider((id,inv,p)->new BotControlMenu(id,viewer),Component.literal("Companions | Bot Controls")));
    }
    private BotControlMenu(int id,ServerPlayer viewer) {
        super(MenuType.GENERIC_9x6,id,viewer.getInventory(),new SimpleContainer(54),6);
        this.viewer=viewer;targetPlayer=viewer.getPlainTextName();render();
    }
    @Override public boolean stillValid(Player player) {return player==viewer && viewer.isAlive() && viewer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);}
    @Override public ItemStack quickMoveStack(Player player,int slot) {return ItemStack.EMPTY;}
    @Override public boolean canDragTo(Slot slot) {return false;}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot) {return false;}
    @Override public void clicked(int slot,int button,ContainerInput type,Player player) {
        if(!stillValid(player)){viewer.closeContainer();return;}
        long now=viewer.level().getGameTime();
        if(MenuClickPolicy.activate(slot,button,type,getCarried().isEmpty()) && now>=clickAt) {
            var action=buttons.get(slot);
            if(action!=null) {clickAt=now+3;action.run();if(viewer.containerMenu==this)render();}
        }
        // Reject shift-click, drag, swap, drop, clone and all player-slot edits.
        // Resync also undoes vanilla client's optimistic pickup of an icon.
        if(viewer.containerMenu==this)sendAllDataToRemote();
    }
    @Override public void broadcastChanges() {
        if(viewer!=null && viewer.level().getGameTime()>=refreshAt) {
            refreshAt=viewer.level().getGameTime()+20;render();
        }
        super.broadcastChanges();
    }
    private List<CompanionController> bots() {return CompanionsMod.manager(viewer.level().getServer()).all().stream().sorted(Comparator.comparing(c->c.player.getPlainTextName())).toList();}
    private CompanionController current() {return bots().stream().filter(c->c.player.getPlainTextName().equals(selected)).findFirst().orElse(null);}
    private static Item item(String id) {return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));}
    private void icon(int slot,Item item,String title,String lore,Runnable action) {
        var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,Component.literal(title));
        stack.set(DataComponents.LORE,new ItemLore(Arrays.stream(lore.split("\n")).limit(20).map(s->(Component)Component.literal(s)).toList()));
        getContainer().setItem(slot,stack);if(action!=null)buttons.put(slot,action);
    }
    private void command(int slot,Item item,String title,String command) {
        icon(slot,item,title,"/"+command+"\nApplies to: "+selected,()->execute(command));
    }
    private void action(int slot,Item item,String title,String action) {command(slot,item,title,"bot "+action+" "+selected);}
    private void execute(String command) {
        if(!stillValid(viewer)){viewer.closeContainer();return;}
        var source=viewer.createCommandSourceStack();
        var dispatcher=viewer.level().getServer().getCommands().getDispatcher();
        try {
            int result=dispatcher.execute(command,source);
            message=result>0?"Applied: /"+command:"No bots changed; see chat for details.";
        } catch(com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException | IllegalStateException e) {
            message=e.getMessage()==null?"Action failed":e.getMessage();
            viewer.sendSystemMessage(Component.literal(message));
        }
    }
    private void tab(int slot,Item item,String name) {icon(slot,item,(page.equals(name)?"> ":"")+name,"Click to open "+name,()->page=name);}
    private void render() {
        buttons.clear();getContainer().clearContent();
        var all=bots();
        icon(0,Items.NETHER_STAR,(selected.equals("all")?"> ":"")+"All bots ("+all.size()+"/4)","Select all active companions",()->{selected="all";page="Actions";});
        for(int i=0;i<Math.min(4,all.size());i++) {
            var bot=all.get(i);String name=bot.player.getPlainTextName();
            icon(i+1,Items.PLAYER_HEAD,(selected.equals(name)?"> ":"")+name,bot.status(),()->{selected=name;page="Actions";});
        }
        icon(7,Items.PAPER,"Selected: "+selected+" | "+page,message,null);
        icon(8,Items.BARRIER,"Close","Close the menu",viewer::closeContainer);
        tab(45,Items.COMPASS,"Actions");tab(46,Items.IRON_PICKAXE,"Mining");tab(47,Items.BRICKS,"Building");
        tab(48,Items.REPEATER,"Settings");tab(49,Items.CHEST,"Inventory");tab(50,Items.PLAYER_HEAD,"Manage");tab(51,Items.LEAD,"Players");
        icon(53,Items.REDSTONE_BLOCK,"STOP ALL","Cancel every bot's task immediately",()->execute("bot stop all"));
        if(!selected.equals("all") && current()==null) {
            icon(22,Items.BARRIER,"Bot is offline","Select another bot or All. No action was redirected.",null);return;
        }
        switch(page) {
            case "Mining" -> mining();
            case "Building" -> building();
            case "Settings" -> settings();
            case "Inventory" -> inventory();
            case "Manage" -> manage();
            case "Players" -> players();
            case "Remove?" -> confirmRemove();
            default -> actions();
        }
    }
    private void actions() {
        command(10,Items.LEAD,"Follow "+targetPlayer,"bot follow "+selected+" "+targetPlayer);
        command(11,Items.COMPASS,"Return to "+targetPlayer+" once","bot return "+selected+" "+targetPlayer);
        command(12,Items.DIAMOND_SWORD,"Protect "+targetPlayer,"bot protect "+selected+" "+targetPlayer);action(13,Items.SHIELD,"Guard current location","guard");
        icon(14,Items.STICK,"Select guard area","Receive the wand, then left/right click the corners.\nReopen with /botui afterward.",()->{viewer.closeContainer();execute("bot guardarea "+selected);});
        action(15,Items.REDSTONE_TORCH,"Stay here","stay");action(16,Items.BARRIER,"Stop selected bots","stop");
        action(19,item("minecraft:red_bed"),"Save bot's current position as home","sethome");action(20,Items.ENDER_PEARL,"Travel to saved home","home");
        var p=viewer.blockPosition();command(21,Items.MAP,"Go to my current block","bot goto "+selected+" "+p.getX()+" "+p.getY()+" "+p.getZ());
        command(22,Items.FILLED_MAP,"Explore 32 blocks","bot explore "+selected+" 32");action(23,Items.FEATHER,"Escape danger","escape");
        action(24,item("minecraft:white_wool"),"Passive mode","passive");command(25,Items.IRON_SWORD,"Attack nearest threat","bot attack "+selected+" nearest");
        command(28,Items.HOPPER,"Collect nearby drops","bot collect "+selected+" drops");action(29,Items.APPLE,"Eat available food","eat");
        command(30,Items.DIAMOND_CHESTPLATE,"Equip best owned equipment","bot equip "+selected+" best");action(31,Items.WRITABLE_BOOK,"Print detailed status to chat","status");
    }
    private void mining() {
        icon(10,Items.PAPER,"Amount per bot: "+amount,"Click to cycle 1 / 9 / 32 / 64 / 128",()->amount=amount==1?9:amount==9?32:amount==32?64:amount==64?128:1);
        for(int i=0;i<UiPresets.MINERALS.size();i++) {
            String id=UiPresets.MINERALS.get(i);
            command(18+i,BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:"+id)),"Mine "+id+" x"+amount,UiPresets.mine(selected,id,amount));
        }
        icon(35,Items.BOOK,"Other blocks / custom quantities","Use /bot mine <name|all> <block> [amount]\nMining returns to follow you when finished.",null);
    }
    private void building() {
        icon(10,Items.PAPER,"Size: "+size,"Click to cycle 5 / 10 / 20 / 32\nWall height: 3; floors are square.",()->size=size==5?10:size==10?20:size==20?32:5);
        for(int i=0;i<UiPresets.SHAPES.size();i++) {
            String shape=UiPresets.SHAPES.get(i);command(19+i,Items.COBBLESTONE,"Build "+shape,UiPresets.build(selected,shape,size));
        }
        icon(31,Items.BOOK,"Build origin","Plans start at each bot's location and facing.\nSelect one bot to avoid overlapping builds.\nCustom sizes: /bot build <name> ...",null);
    }
    private void settings() {
        for(int i=0;i<UiPresets.FLAGS.size();i++) {
            String flag=UiPresets.FLAGS.get(i);
            command(9+i,item("minecraft:lime_dye"),flag+": ON","bot config "+selected+" "+flag+" on");
            command(18+i,item("minecraft:gray_dye"),flag+": OFF","bot config "+selected+" "+flag+" off");
        }
        command(28,Items.APPLE,"Health: 20 (heals bots)","bot health "+selected+" 20");
        command(29,Items.GOLDEN_APPLE,"Health: 40 (heals bots)","bot health "+selected+" 40");
        command(30,Items.ENCHANTED_GOLDEN_APPLE,"Health: 100 (heals bots)","bot health "+selected+" 100");
        command(32,Items.COMPASS,"Max distance: 256","bot config "+selected+" maxDistance 256");
        var c=current();if(c!=null)icon(36,Items.PAPER,"Current settings","sprint="+c.config.sprint+" breakBlocks="+c.config.breakBlocks+"\nplaceBlocks="+c.config.placeBlocks+" bridge="+c.config.bridge+"\npvp="+c.config.pvp+" friendlyFire="+c.config.friendlyFire+"\nmaxDistance="+c.config.maxDistance+" maxHealth="+c.config.maxHealth,null);
    }
    private void inventory() {
        var c=current();
        if(c==null){icon(22,Items.CHEST,"Select one bot above","View its 36 inventory slots.\nDisplay only: icons cannot be taken.",null);return;}
        for(int i=0;i<36;i++)getContainer().setItem(9+i,c.player.getInventory().getItem(i).copy());
    }
    private void manage() {
        String[] names={"bob","alice","steve","alex"};
        for(int i=0;i<names.length;i++)command(10+i,Items.PLAYER_HEAD,"Spawn / restore "+names[i],"bot spawn "+names[i]);
        icon(22,Items.BARRIER,"Despawn "+selected,"Click for confirmation. Saved inventory is retained.",()->page="Remove?");
        icon(31,Items.BOOK,"Custom names and parameters","/bot spawn <name>\n/bot protect <name|all> <player>\n/bot follow <name|all> <player>\n/botui to reopen this menu",null);
    }
    private void confirmRemove() {
        icon(21,item("minecraft:red_concrete"),"Confirm despawn: "+selected,"Saves each bot before despawning.",()->{execute("bot remove "+selected);selected="all";page="Manage";});
        icon(23,item("minecraft:lime_concrete"),"Cancel","Return to management",()->page="Manage");
    }
    private void players() {
        var players=viewer.level().getServer().getPlayerList().getPlayers().stream()
            .filter(p->!(p instanceof dev.companions.player.CompanionPlayer))
            .sorted(Comparator.comparing(ServerPlayer::getPlainTextName)).toList();
        int maxPage=Math.max(0,(players.size()-1)/18);playerPage=Math.min(playerPage,maxPage);
        icon(10,Items.PAPER,"Follow / protect target: "+targetPlayer,"Choose an online player, then an action.\nPage "+(playerPage+1)+"/"+(maxPage+1),null);
        for(int i=0;i<18 && playerPage*18+i<players.size();i++) {
            String name=players.get(playerPage*18+i).getPlainTextName();
            icon(18+i,Items.PLAYER_HEAD,name,"Select for Follow / Return / Protect",()->{targetPlayer=name;page="Actions";});
        }
        if(playerPage>0)icon(36,Items.ARROW,"Previous players","Previous page",()->playerPage--);
        if(playerPage<maxPage)icon(44,Items.ARROW,"More players","Next page",()->playerPage++);
    }
}
