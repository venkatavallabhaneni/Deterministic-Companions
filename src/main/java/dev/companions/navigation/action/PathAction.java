package dev.companions.navigation.action;

public enum PathAction {
    MOVE(1), SPRINT(1), JUMP(1.6), STEP_UP(1.3), SAFE_FALL(1.8), SWIM(2.5),
    CLIMB(2.5), OPEN_DOOR(2), BREAK(8), PLACE(12), BRIDGE_FORWARD(12), PILLAR_UP(15), DIG_DOWN_SAFE(10);
    private final double cost;
    PathAction(double cost) { this.cost = cost; }
    public double cost() { return cost; }
}
