package dev.companions.schematic;

import dev.companions.navigation.TraversalGeometry;
import dev.companions.player.CompanionController;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Places one exact schematic state with the companion's real inventory item. */
public final class SchematicPlacer {
    public enum Result {PLACED,NO_ITEM,NO_FACE,OBSTRUCTED,FAILED}
    public static final double REACH_SQ=20.25;
    private SchematicPlacer(){}

    public static boolean inReach(CompanionController c,BlockPos pos){return c.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))<=REACH_SQ;}
    /** A solid neighbour face that is within reach and visible from the companion's eyes. */
    public static boolean hasFace(CompanionController c,BlockPos pos,BlockState target){
        for(Direction toward:PlacementRules.supportOrder(target))if(hit(c,pos,toward,target)!=null)return true;
        return false;
    }
    private static Vec3 hit(CompanionController c,BlockPos pos,Direction toward,BlockState target){
        var level=c.player.level();
        BlockPos neighbor=pos.relative(toward);
        if(!level.hasChunkAt(neighbor)||level.getBlockState(neighbor).getCollisionShape(level,neighbor).isEmpty())return null;
        Direction face=toward.getOpposite();
        Vec3 point=Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
        // Clicking the upper part of a side face places top slabs/stairs/trapdoors.
        if(toward.getAxis().isHorizontal()&&PlacementRules.supportOrder(target).getFirst()==Direction.UP)point=point.add(0,0.25,0);
        if(c.player.getEyePosition().distanceToSqr(point)>REACH_SQ)return null;
        if(!TraversalGeometry.visible(level,c.player,c.player.getEyePosition(),point,neighbor))return null;
        return point;
    }
    /** True when an entity (often the companion itself or another builder) occupies the cell. */
    public static boolean obstructed(CompanionController c,BlockPos pos,BlockState target){
        var level=c.player.level();
        return !level.isUnobstructed(target,pos,CollisionContext.empty());
    }

    public static Result place(CompanionController c,BlockPos pos,BlockState target,Item item){
        var level=c.player.level();
        if(!level.getBlockState(pos).canBeReplaced())return Result.FAILED;
        int slot=c.inventory.find(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
        if(slot<0)return Result.NO_ITEM;
        if(obstructed(c,pos,target))return Result.OBSTRUCTED;
        if(!level.mayInteract(c.player,pos)||level.getServer().isUnderSpawnProtection(level,pos,c.player))return Result.FAILED;
        c.inventory.select(slot);c.player.stopUsingItem();
        Direction yaw=PlacementRules.placerFacing(target);
        boolean anyFace=false;
        boolean chest=target.getBlock() instanceof ChestBlock;
        for(Direction toward:PlacementRules.supportOrder(target)){
            // Sneak-clicking the side of a chest with a chest joins the two into a double chest
            // facing the clicked one's way. Never click another chest's side when placing a chest.
            if(chest&&toward.getAxis().isHorizontal()&&level.getBlockState(pos.relative(toward)).getBlock() instanceof ChestBlock)continue;
            Vec3 point=hit(c,pos,toward,target);
            if(point==null)continue;
            anyFace=true;
            c.actions.lookAt(point);
            if(yaw!=null){c.player.setYRot(yaw.toYRot());c.player.setYHeadRot(yaw.toYRot());}
            boolean shifted=c.player.isShiftKeyDown();c.player.setShiftKeyDown(true);
            try{c.player.gameMode.useItemOn(c.player,level,c.player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(point,toward.getOpposite(),pos.relative(toward),false));}
            finally{c.player.setShiftKeyDown(shifted);}
            c.player.swing(InteractionHand.MAIN_HAND);
            BlockState placed=level.getBlockState(pos);
            if(placed.getBlock().asItem()==item&&!placed.isAir()){
                correct(c,pos,placed,target);
                return Result.PLACED;
            }
        }
        return anyFace?Result.FAILED:Result.NO_FACE;
    }
    /**
     * The real item was consumed by a vanilla placement; align the orientation
     * or variant (wall vs floor torch, top slab, chest facing ...) with the
     * schematic. Double slabs spend their second slab here.
     */
    private static void correct(CompanionController c,BlockPos pos,BlockState placed,BlockState target){
        var level=c.player.level();
        if(target.getBlock() instanceof ChestBlock&&placed.getBlock()==target.getBlock()){pairChest(level,pos,target);return;}
        if(PlacementRules.matches(placed,target))return;
        BlockState wanted=PlacementRules.adjust(placed,target);
        if(placed.getBlock()!=target.getBlock()&&!target.canSurvive(level,pos))return;
        if(PlacementRules.itemCount(target)==2&&!PlacementRules.matches(placed,target)){
            int slot=c.inventory.find(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(PlacementRules.item(target)).toString());
            if(slot<0)return;
            c.player.getInventory().getItem(slot).shrink(1);
        }
        BlockPos partnerPos=null;BlockState partner=null;
        var direction=PlacementRules.partnerDirection(target);
        if(direction!=null){
            BlockPos oldPartner=PlacementRules.partnerDirection(placed)==null?null:pos.relative(PlacementRules.partnerDirection(placed));
            partnerPos=pos.relative(direction);partner=PlacementRules.partnerState(wanted);
            if(!partnerPos.equals(oldPartner)){
                // A mis-oriented bed head landed elsewhere; only realign if the right cell is free.
                if(!level.getBlockState(partnerPos).canBeReplaced())return;
                if(oldPartner!=null&&level.getBlockState(oldPartner).getBlock()==placed.getBlock())level.setBlock(oldPartner,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            }
        }
        level.setBlock(pos,shaped(wanted,level,pos),Block.UPDATE_ALL);
        if(partnerPos!=null)level.setBlock(partnerPos,partner,Block.UPDATE_ALL);
        c.player.inventoryMenu.broadcastChanges();
    }
    /**
     * Gives the chest at pos the schematic's facing and pairing, and keeps both
     * halves of every pair consistent. A schematic double-chest half pairs only
     * once its partner exists with the same facing (both halves are then set);
     * until then it stays single. Any neighbouring chest that believes it is
     * paired with pos, but should not be, is made single again.
     */
    static void pairChest(net.minecraft.world.level.Level level,BlockPos pos,BlockState target){
        BlockState here=target;
        BlockPos partner=null;
        if(target.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
            partner=ChestBlock.getConnectedBlockPos(pos,target);
            BlockState other=level.getBlockState(partner);
            if(other.getBlock()==target.getBlock()&&other.getValue(ChestBlock.FACING)==target.getValue(ChestBlock.FACING))
                level.setBlock(partner,other.setValue(ChestBlock.TYPE,target.getValue(ChestBlock.TYPE).getOpposite()),Block.UPDATE_ALL);
            else{here=target.setValue(ChestBlock.TYPE,ChestType.SINGLE);partner=null;}
        }
        for(Direction d:Direction.Plane.HORIZONTAL){
            BlockPos n=pos.relative(d);
            if(n.equals(partner))continue;
            BlockState ns=level.getBlockState(n);
            if(ns.getBlock() instanceof ChestBlock&&ns.getValue(ChestBlock.TYPE)!=ChestType.SINGLE&&ChestBlock.getConnectedBlockPos(n,ns).equals(pos))
                level.setBlock(n,ns.setValue(ChestBlock.TYPE,ChestType.SINGLE),Block.UPDATE_ALL);
        }
        level.setBlock(pos,here,Block.UPDATE_ALL);
    }
    /**
     * Same block, wrong orientation or pairing (a chest joined to the wrong
     * neighbour, a door hinge, a stair): fix it in place without new items.
     */
    public static boolean realign(CompanionController c,BlockPos pos,BlockState target){
        var level=c.player.level();
        BlockState actual=level.getBlockState(pos);
        if(actual.getBlock()!=target.getBlock()||!inReach(c,pos))return false;
        if(!level.mayInteract(c.player,pos)||level.getServer().isUnderSpawnProtection(level,pos,c.player))return false;
        c.actions.lookAt(Vec3.atCenterOf(pos));c.player.swing(InteractionHand.MAIN_HAND);
        if(target.getBlock() instanceof ChestBlock){pairChest(level,pos,target);return true;}
        if(PlacementRules.partnerDirection(target)!=null){
            BlockPos partnerPos=pos.relative(PlacementRules.partnerDirection(target));
            BlockState there=level.getBlockState(partnerPos);
            if(there.getBlock()!=target.getBlock()&&!there.canBeReplaced())return false;
            level.setBlock(pos,PlacementRules.adjust(actual,target),Block.UPDATE_ALL);
            level.setBlock(partnerPos,PlacementRules.partnerState(PlacementRules.adjust(actual,target)),Block.UPDATE_ALL);
            return true;
        }
        level.setBlock(pos,shaped(PlacementRules.adjust(actual,target),level,pos),Block.UPDATE_ALL);
        return true;
    }
    /** Neighbour-derived shape (fence/pane links, stair corners). Two-part blocks are set as-is:
     * a lone door or bed half would otherwise resolve to air before its partner exists. */
    private static BlockState shaped(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos){
        return PlacementRules.partnerDirection(state)==null?Block.updateFromNeighbourShapes(state,level,pos):state;
    }
    /**
     * Last resort for a state vanilla item use cannot reproduce (no clickable
     * support, unusual variants). Still consumes the real item and respects
     * reach, spawn protection and interaction permission.
     */
    public static boolean force(CompanionController c,BlockPos pos,BlockState target,Item item){
        var level=c.player.level();
        if(!inReach(c,pos)||!level.getBlockState(pos).canBeReplaced()||obstructed(c,pos,target))return false;
        if(!level.mayInteract(c.player,pos)||level.getServer().isUnderSpawnProtection(level,pos,c.player))return false;
        String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        int count=PlacementRules.itemCount(target);
        if(c.inventory.count(id)<count)return false;
        var direction=PlacementRules.partnerDirection(target);
        BlockPos partnerPos=direction==null?null:pos.relative(direction);
        if(partnerPos!=null&&!level.getBlockState(partnerPos).canBeReplaced())return false;
        for(int i=0;i<36&&count>0;i++){var s=c.player.getInventory().getItem(i);if(s.is(item)){int n=Math.min(count,s.getCount());s.shrink(n);count-=n;}}
        c.actions.lookAt(Vec3.atCenterOf(pos));c.player.swing(InteractionHand.MAIN_HAND);
        if(target.getBlock() instanceof ChestBlock)pairChest(level,pos,target);
        else level.setBlock(pos,shaped(target,level,pos),Block.UPDATE_ALL);
        if(partnerPos!=null)level.setBlock(partnerPos,PlacementRules.partnerState(target),Block.UPDATE_ALL);
        var sound=target.getSoundType();
        level.playSound(null,pos,sound.getPlaceSound(),net.minecraft.sounds.SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*0.8f);
        c.player.inventoryMenu.broadcastChanges();
        return true;
    }
}
