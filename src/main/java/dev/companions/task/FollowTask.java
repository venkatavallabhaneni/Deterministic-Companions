package dev.companions.task;

public final class FollowTask extends ReturnTask {
    public FollowTask(TrackingTarget target) {super(target);}
    @Override public void tick() {
        status="FOLLOW "+target.name();
        if(!target.available()) {target.halt();status+=" (waiting for player in same dimension)";return;}
        if(target.nearby()) target.halt(); else target.approach();
    }
}
