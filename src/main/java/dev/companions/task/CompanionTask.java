package dev.companions.task;

public interface CompanionTask {
    default void start() {}
    void tick();
    void cancel();
    boolean isComplete();
    String getStatus();
}
