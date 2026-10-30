package dev.companions.navigation.path;

import dev.companions.navigation.action.PathAction;
import java.util.List;

public record PathEdge(Cell destination, PathAction action, List<Cell> breaks, Cell placement, double extraCost) {
    public PathEdge { breaks = List.copyOf(breaks); }
    public double cost() { return action.cost() + Math.max(0, extraCost); }
    public static PathEdge move(Cell to, PathAction action) { return new PathEdge(to, action, List.of(), null, 0); }
}
