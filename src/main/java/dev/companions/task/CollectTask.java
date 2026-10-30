package dev.companions.task;

import dev.companions.player.CompanionController;
import dev.companions.inventory.InventoryController;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.Comparator;

public final class CollectTask extends BaseTask {
    private final String item;
    private final int amount,initial;
    private int quiet,ticks;
    public CollectTask(CompanionController c,String item,int amount) {super(c,"COLLECT");this.item=item;this.amount=amount;initial=item==null?0:c.inventory.count(item);}
    public static ItemEntity nearest(CompanionController c,String item,double radius) {
        return c.player.level().getEntitiesOfClass(ItemEntity.class,c.player.getBoundingBox().inflate(radius),e -> e.isAlive() && !e.hasPickUpDelay() && (item==null||InventoryController.id(e.getItem()).equals(item)))
            .stream().min(Comparator.comparingDouble((ItemEntity e) -> c.player.distanceToSqr(e)).thenComparingInt(ItemEntity::getId)).orElse(null);
    }
    @Override public void tick() {
        int progress=item==null?0:Math.max(0,c.inventory.count(item)-initial);
        status="COLLECT "+(item==null?"drops":item+" "+progress+"/"+amount);
        if(ticks>=1200){finish(status+" — collection timed out");return;}
        if(item!=null&&progress>=amount) {finish(status+" complete");return;}
        if(++ticks%10!=1) return;
        var drop=nearest(c,item,16);
        if(drop==null) {c.navigation.stop();if(++quiet>6) finish(status+" — no nearby drops");return;}
        if(!c.inventory.canAccept(drop.getItem())){finish(status+" — inventory full");return;}
        quiet=0;c.navigation.target(drop.position(),0.5);routeFailed();
    }
}
