package dev.companions.task;

/** Testable contract: approach must refresh the destination from the live player. */
public interface TrackingTarget {
    boolean available();
    boolean nearby();
    void approach();
    void halt();
    boolean blocked();
    String name();
}
