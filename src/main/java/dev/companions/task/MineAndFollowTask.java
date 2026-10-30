package dev.companions.task;

/** Mining owns control until it finishes (including a reported failure), then follow persists. */
public final class MineAndFollowTask implements CompanionTask {
    private final CompanionTask mining,follow;
    private final Runnable report;
    private boolean following,cancelled;
    public MineAndFollowTask(CompanionTask mining,CompanionTask follow,Runnable report){this.mining=mining;this.follow=follow;this.report=report;}
    public void start(){mining.start();}
    public void tick(){
        if(cancelled)return;
        if(following){follow.tick();return;}
        mining.tick();
        if(mining.isComplete()){report.run();following=true;follow.start();}
    }
    public void cancel(){cancelled=true;if(following)follow.cancel();else mining.cancel();}
    public boolean isComplete(){return cancelled;}
    public String getStatus(){return following?"MINING finished; "+follow.getStatus():mining.getStatus();}
}
