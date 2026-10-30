package dev.companions.navigation.path;

public record Cell(int x, int y, int z) {
    public Cell add(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    public double distance(Cell b) { return Math.sqrt(distanceSquared(b)); }
    public long distanceSquared(Cell b) { long dx=x-b.x,dy=y-b.y,dz=z-b.z; return dx*dx+dy*dy+dz*dz; }
}
