package dev.companions.world;

import dev.companions.player.CompanionController;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class WorldActionController {
    public enum Result { RUNNING, SUCCESS, BLOCKED }
    private final CompanionController c;
    private BlockPos breaking;
    private float progress;
    private int sequence;
    public WorldActionController(CompanionController c) { this.c=c; }
    public void lookAt(Vec3 target) {
        Vec3 d=target.subtract(c.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
        c.player.setYRot(yaw); c.player.setYHeadRot(yaw);
        c.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
    }
    public void halt() { c.player.zza=0; c.player.xxa=0; c.player.setSprinting(false); c.player.setShiftKeyDown(false); }
    public void move(Vec3 point, boolean sprint) {
        lookAt(new Vec3(point.x,c.player.getEyeY(),point.z)); c.player.zza=1; c.player.xxa=0;
        c.player.setSprinting(sprint && c.config.sprint && c.player.getFoodData().getFoodLevel()>6 && !c.player.isUsingItem() && !c.player.isShiftKeyDown());
    }
    public void jump() { if(c.player.onGround()) c.player.jumpFromGround(); else if(c.player.isInWater()) c.player.setDeltaMovement(c.player.getDeltaMovement().add(0,0.04,0)); }
    public void attack(net.minecraft.world.entity.LivingEntity target) {
        if(c.combat.allowed(target) && c.player.isWithinEntityInteractionRange(target,0) && c.player.hasLineOfSight(target) && c.player.getAttackStrengthScale(0)>=1) {
            lookAt(target.getEyePosition());c.player.attack(target);c.player.swing(InteractionHand.MAIN_HAND);
        }
    }
    public boolean visible(BlockPos pos) {
        return dev.companions.navigation.TraversalGeometry.visible(c.player.level(),c.player,c.player.getEyePosition(),Vec3.atCenterOf(pos),pos);
    }
    public boolean reachable(BlockPos pos) { return c.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))<=20.25 && visible(pos); }
    public boolean mayBreak(BlockPos pos) {
        return mayBreak(pos,false);
    }
    public boolean mayBreak(BlockPos pos,boolean explicitTarget) {
        if(!c.player.level().hasChunkAt(pos)||!c.resourceAvailable(pos)||c.construction.protectedBlock.test(pos))return false;
        var s=c.player.level().getBlockState(pos);
        for(Direction d:Direction.values()) {
            BlockPos neighbor=pos.relative(d);
            if(!c.player.level().hasChunkAt(neighbor) || c.player.level().getFluidState(neighbor).is(net.minecraft.tags.FluidTags.LAVA))return false;
        }
        if(pos.equals(c.player.blockPosition().below())) {
            boolean landing=false;
            for(int fall=1;fall<=3;fall++) {
                var cell=dev.companions.navigation.WorldNeighbors.cell(pos.below(fall));
                if(c.navigation.world.hazard(cell))break;
                if(c.navigation.world.support(cell)||c.navigation.world.water(cell)){landing=true;break;}
            }
            if(!landing)return false;
        }
        return c.config.breakBlocks && c.player.level().mayInteract(c.player,pos)
            && !c.player.level().getServer().isUnderSpawnProtection(c.player.level(),pos,c.player)
            && BlockSafetyPolicy.mayBreak(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(),s.hasBlockEntity(),s.getDestroySpeed(c.player.level(),pos),explicitTarget);
    }
    public void cancelBreak() {
        if(breaking!=null) c.player.gameMode.handleBlockBreakAction(breaking,ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,Direction.UP,c.player.level().getMaxY(),sequence++);
        breaking=null; progress=0;
        c.resources.stopBreaking(c.player.getUUID());
    }
    public Result breakBlock(BlockPos pos) {
        return breakBlock(pos,false);
    }
    public Result breakBlock(BlockPos pos,boolean explicitTarget) {
        if(!c.player.level().hasChunkAt(pos))return Result.BLOCKED;
        if(c.player.level().getBlockState(pos).isAir()) {cancelBreak();return Result.SUCCESS;}
        if(!mayBreak(pos,explicitTarget) || !reachable(pos)) {cancelBreak();return Result.BLOCKED;}
        halt(); lookAt(Vec3.atCenterOf(pos));
        if(!pos.equals(breaking)) {
            cancelBreak(); c.inventory.tool(c.player.level().getBlockState(pos)); breaking=pos.immutable();
            if(!c.resources.startBreaking(c.player.getUUID(),c.player.level().dimension().identifier().toString(),pos.asLong(),c.player.level().getGameTime())){breaking=null;return Result.BLOCKED;}
            c.player.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.UP,c.player.level().getMaxY(),sequence++);
        }
        if(!c.resources.startBreaking(c.player.getUUID(),c.player.level().dimension().identifier().toString(),pos.asLong(),c.player.level().getGameTime())){cancelBreak();return Result.BLOCKED;}
        c.inventory.tool(c.player.level().getBlockState(pos));
        progress+=c.player.level().getBlockState(pos).getDestroyProgress(c.player,c.player.level(),pos);
        c.player.swing(InteractionHand.MAIN_HAND);
        // A remote client normally sends STOP when its local progress reaches
        // completion. Fake players have no client, so invoke the same vanilla
        // server-authoritative destroy path directly after applying normal tool
        // speed. This runs block hooks, drops, tool wear and permissions.
        if(progress>=1) {
            boolean destroyed=c.player.gameMode.destroyBlock(pos);
            if(destroyed||c.player.level().getBlockState(pos).isAir()){cancelBreak();c.construction.broken.accept(pos.immutable());return Result.SUCCESS;}
            cancelBreak();return Result.BLOCKED;
        }
        if(c.player.level().getBlockState(pos).isAir()) {cancelBreak();return Result.SUCCESS;}
        return Result.RUNNING;
    }
    public Result placeBlock(BlockPos pos,String specified) {
        return placeBlock(pos,specified,null);
    }
    public Result placeBlock(BlockPos pos,String specified,String expectedBlock) {
        if(!c.player.level().hasChunkAt(pos) || c.player.level().isOutsideBuildHeight(pos))return Result.BLOCKED;
        if(!c.config.placeBlocks || !c.player.level().getBlockState(pos).canBeReplaced()) return Result.BLOCKED;
        int slot=specified==null?c.inventory.buildingSlot():c.inventory.find(specified);
        if(slot<0) return Result.BLOCKED;
        var item=c.player.getInventory().getItem(slot);
        if(!(item.getItem() instanceof BlockItem blockItem)) return Result.BLOCKED;
        // Selecting a non-hotbar slot swaps it with the held slot. Do this only once,
        // otherwise trying a second face swaps the sword back into the placement hand.
        c.inventory.select(slot);c.player.stopUsingItem();
        java.util.List<Direction> faces=new java.util.ArrayList<>(java.util.List.of(Direction.values()));
        for(Direction face:faces) {
            BlockPos neighbor=pos.relative(face.getOpposite());
            if(!c.player.level().hasChunkAt(neighbor))continue;
            var state=c.player.level().getBlockState(neighbor);
            if(state.getCollisionShape(c.player.level(),neighbor).isEmpty()) continue;
            Vec3 hit=Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
            if(c.player.getEyePosition().distanceToSqr(hit)>20.25) continue;
            if(!dev.companions.navigation.TraversalGeometry.visible(c.player.level(),c.player,c.player.getEyePosition(),hit,neighbor))continue;
            if(!c.player.level().mayInteract(c.player,pos) || c.player.level().getServer().isUnderSpawnProtection(c.player.level(),pos,c.player)) return Result.BLOCKED;
            lookAt(hit);
            boolean shifted=c.player.isShiftKeyDown();c.player.setShiftKeyDown(true);
            try{c.player.gameMode.useItemOn(c.player,c.player.level(),c.player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,neighbor,false));}
            finally{c.player.setShiftKeyDown(shifted);}
            c.player.swing(InteractionHand.MAIN_HAND);
            String placed=BuiltInRegistries.BLOCK.getKey(c.player.level().getBlockState(pos).getBlock()).toString();
            if(expectedBlock==null?c.player.level().getBlockState(pos).is(blockItem.getBlock()):placed.equals(expectedBlock)) {
                return Result.SUCCESS;
            }
        }
        return Result.BLOCKED;
    }
    public boolean useBlock(BlockPos pos) {
        if(!c.player.level().hasChunkAt(pos)) return false;
        if(!reachable(pos)) {
            if(!dev.companions.world.InteractionPosition.approach(c,pos)) return false;
            return false;
        }
        halt();c.player.stopUsingItem();lookAt(Vec3.atCenterOf(pos));
        // An empty interaction hand avoids held block/item actions consuming the workstation click.
        // The actual stack remains in a local variable and is restored even if a hook throws.
        int selected=c.player.getInventory().getSelectedSlot();
        var beforeMenu=c.player.containerMenu;
        try {
            boolean used=dev.companions.inventory.HeldItemInteraction.emptyHand(c.player.getInventory().getNonEquipmentItems(),selected,
                ()->c.player.gameMode.useItemOn(c.player,c.player.level(),c.player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)).consumesAction(),
                received->{c.player.getInventory().add(received);if(!received.isEmpty())c.player.drop(received,false,false);});
            // Buttons, levers, doors and trapdoors have no menu. Their
            // successful interaction is still the boolean consumed-action
            // result and must be reported to navigation.
            if(used)return true;
            if(c.player.containerMenu!=beforeMenu)return true;
            // Local fake connections intentionally discard packets. If the game-mode
            // interaction did not open the vanilla menu, invoke the block's own menu
            // provider on the server, preserving the same reach/permission checks.
            var provider=c.player.level().getBlockState(pos).getMenuProvider(c.player.level(),pos);
            return provider!=null && c.player.openMenu(provider).isPresent();
        }finally{
            c.player.inventoryMenu.broadcastChanges();
        }
    }
}
