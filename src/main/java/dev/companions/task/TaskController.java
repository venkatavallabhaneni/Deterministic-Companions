package dev.companions.task;

/** One owner of explicit task execution. Replacement always cancels first. */
public final class TaskController {
    private CompanionTask current;
    private String lastStatus = "IDLE";
    private final Runnable halt;
    private Runnable completion=() -> {};
    public TaskController(Runnable halt) { this.halt = halt; }
    public void set(CompanionTask task) {
        set(task,() -> {});
    }
    public void set(CompanionTask task,Runnable completion) {
        stop();
        current = task;
        this.completion=completion;
        task.start();
    }
    public void stop() {
        if (current != null) current.cancel();
        current = null;
        completion=() -> {};
        halt.run();
        lastStatus = "IDLE";
    }
    public void tick() {
        if (current == null) return;
        current.tick();
        if (current.isComplete()) {
            lastStatus = current.getStatus();
            current = null;
            halt.run();
            Runnable callback=completion;
            completion=() -> {};
            callback.run();
        }
    }
    public boolean idle() { return current == null; }
    public CompanionTask current(){return current;}
    public String status() { return current == null ? lastStatus : current.getStatus(); }
}
