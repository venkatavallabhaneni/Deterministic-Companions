package dev.companions.task;

import dev.companions.player.CompanionController;
import dev.companions.world.*;
import net.minecraft.core.BlockPos;

public final class BreakTask extends BaseTask {
    private final BlockPos block;
    private int ticks;
    public BreakTask(CompanionController c,BlockPos block) {super(c,"DIG "+block.toShortString());this.block=block;}
    @Override public void tick() {
        if(++ticks>1200) {finish("DIG timed out");return;}
        if(!c.player.level().hasChunkAt(block)){finish("DIG: target chunk is not loaded");return;}
        if(c.player.level().getBlockState(block).isAir()) {finish("DIG complete");return;}
        if(!c.actions.mayBreak(block,true)) {finish("DIG: protected block, unsafe position, or breaking disabled");return;}
        if(!c.inventory.hasHarvestTool(c.player.level().getBlockState(block))) {finish("DIG: a suitable usable tool is required to preserve the block's drops");return;}
        if(c.actions.reachable(block)) {
            c.navigation.pause();
            var result=c.actions.breakBlock(block,true);
            if(result==WorldActionController.Result.SUCCESS) finish("DIG complete");
            else if(result==WorldActionController.Result.BLOCKED)finish("DIG: block interaction was denied or became unsafe");
        } else if(ticks%20==1 && !InteractionPosition.approach(c,block)) finish("DIG: no interaction position");
        else routeFailed();
    }
}
