package dev.companions.task;

/** A one-shot return to a LIVE PLAYER; never reads home coordinates. */
public class ReturnTask implements CompanionTask {
    protected final TrackingTarget target;
    protected boolean complete;
    protected String status;
    public ReturnTask(TrackingTarget target) {this.target=target;status="RETURN "+target.name();}
    @Override public void tick() {
        if(!target.available()) {complete=true;status="RETURN: target unavailable";target.halt();return;}
        if(target.nearby()) {complete=true;status="RETURN complete: "+target.name();target.halt();return;}
        if(target.blocked()) {complete=true;status="RETURN blocked";target.halt();return;}
        target.approach();
    }
    @Override public void cancel() {complete=true;target.halt();status="CANCELLED";}
    @Override public boolean isComplete() {return complete;}
    @Override public String getStatus() {return status;}
}
