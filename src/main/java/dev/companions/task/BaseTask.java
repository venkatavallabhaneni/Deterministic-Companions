package dev.companions.task;

import dev.companions.player.CompanionController;

public abstract class BaseTask implements CompanionTask {
    protected final CompanionController c;
    protected boolean complete;
    protected String status;
    protected BaseTask(CompanionController c,String status) {this.c=c;this.status=status;}
    protected void finish(String reason) {complete=true;status=reason;c.combat.resetRanged();c.releaseResource();c.navigation.stop();c.workstations.endSession();c.inventory.stowHeld();}
    @Override public void cancel() {complete=true;c.inventory.swordDuty=false;c.combat.resetRanged();c.releaseResource();c.navigation.stop();c.workstations.endSession();c.inventory.stowHeld();status="CANCELLED";}
    @Override public boolean isComplete() {return complete;}
    @Override public String getStatus() {return status;}
    protected boolean routeFailed() {if(c.navigation.failed()){finish(status+" — "+c.navigation.status());return true;}return false;}
}
