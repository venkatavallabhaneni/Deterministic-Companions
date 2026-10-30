package dev.companions.navigation.path;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Incremental, deterministic A*. Each step has a strict expansion budget. */
public final class BoundedAStar {
    private record Node(Cell cell, double g, double f, long order) {}
    private record Parent(Cell from, PathEdge edge) {}
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f).thenComparingLong(Node::order));
    private final Map<Cell, Double> costs = new HashMap<>();
    private final Map<Cell, Parent> parents = new HashMap<>();
    private final Set<Cell> closed = new HashSet<>();
    private final Cell start, goal;
    private final int radius, maxNodes;
    private final double tolerance;
    private final BiFunction<Cell, PathEdge, List<PathEdge>> neighbors;
    private long serial;
    private boolean done, reached;
    private Cell best;
    public BoundedAStar(Cell start, Cell goal, double tolerance, int radius, int maxNodes, Function<Cell,List<PathEdge>> neighbors) {
        this(start,goal,tolerance,radius,maxNodes,(cell,via)->neighbors.apply(cell));
    }
    /**
     * @param neighbors receives the cell and the edge that reached it on the best route so far
     *        (null at the start), so moves can build on blocks that route will already have placed.
     */
    public BoundedAStar(Cell start, Cell goal, double tolerance, int radius, int maxNodes, BiFunction<Cell,PathEdge,List<PathEdge>> neighbors) {
        this.start=start; this.goal=goal; this.tolerance=tolerance; this.radius=radius; this.maxNodes=maxNodes; this.neighbors=neighbors;
        best=start; costs.put(start,0.0); open.add(new Node(start,0,heuristic(start),serial++));
    }
    public void step(int budget) {
        for (int n=0; n<budget && !done; n++) {
            if (open.isEmpty() || closed.size()>=maxNodes) { done=true; break; }
            Node node=open.poll();
            if (!closed.add(node.cell)) continue;
            if (node.cell.distance(goal)<best.distance(goal)) best=node.cell;
            if (node.cell.distance(goal)<=tolerance) { best=node.cell; reached=done=true; break; }
            Parent via=parents.get(node.cell);
            for (PathEdge edge:neighbors.apply(node.cell,via==null?null:via.edge)) {
                Cell to=edge.destination();
                if (to.distanceSquared(start)>(long)radius*radius || closed.contains(to)) continue;
                double g=node.g+edge.cost();
                if (g>=costs.getOrDefault(to,Double.POSITIVE_INFINITY)) continue;
                costs.put(to,g); parents.put(to,new Parent(node.cell,edge));
                open.add(new Node(to,g,g+heuristic(to),serial++));
            }
        }
    }
    public boolean done() { return done; }
    private double heuristic(Cell p) {
        // A fall edge can descend three blocks for less than Euclidean distance.
        return Math.max(0,Math.max(Math.max(Math.abs(p.x()-goal.x()),Math.abs(p.z()-goal.z())),Math.abs(p.y()-goal.y())/3.0)-tolerance);
    }
    public boolean reached() { return reached; }
    public int explored() { return closed.size(); }
    public List<PathEdge> path() {
        if (!done) throw new IllegalStateException("Search in progress");
        LinkedList<PathEdge> result=new LinkedList<>();
        for (Cell c=best; !c.equals(start); ) {
            Parent p=parents.get(c); if (p==null) break;
            result.addFirst(p.edge); c=p.from;
        }
        return List.copyOf(result);
    }
}
