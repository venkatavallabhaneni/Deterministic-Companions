package dev.companions.task;
public final class ComeTask extends ReturnTask {
    public ComeTask(TrackingTarget target) {super(target);}
    @Override public String getStatus() {return super.getStatus().replace("RETURN","COME");}
}
