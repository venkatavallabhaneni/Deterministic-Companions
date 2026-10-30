package dev.companions.navigation.flight;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Incremental, server-thread 3D A* over four-block air cells. */
public final class FlightPathfinder {
    public interface Air { boolean clear(Vec3 point); }
    private record Cell(int x,int y,int z){}
    private record Node(Cell cell,double cost,double score){}
    private static final int MAX_EXPLORED=700,MAX_HORIZONTAL=18,MAX_VERTICAL=14;
    private static final int[][] MOVES={
        {0,1,0},{1,1,0},{0,1,1},{-1,1,0},{0,1,-1},
        {1,0,0},{0,0,1},{-1,0,0},{0,0,-1},
        {1,-1,0},{0,-1,1},{-1,-1,0},{0,-1,-1},{0,-1,0}
    };
    private final Air air;
    private final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score)
        .thenComparingInt(n->n.cell.x).thenComparingInt(n->n.cell.y).thenComparingInt(n->n.cell.z));
    private final Map<Cell,Double> best=new HashMap<>();
    private final Map<Cell,Cell> parent=new HashMap<>();
    private final ArrayDeque<Vec3> route=new ArrayDeque<>();
    private Vec3 origin,goal;
    private int explored,step=4;
    private boolean failed;
    public FlightPathfinder(Air air){this.air=air;}
    public Vec3 liftGoal(Vec3 desired){
        for(int rise=0;rise<=64;rise+=4){
            Vec3 candidate=desired.add(0,rise,0);
            if(air.clear(candidate))return candidate;
        }
        return desired;
    }
    public void reset(){open.clear();best.clear();parent.clear();route.clear();origin=goal=null;explored=0;failed=false;}
    public boolean failed(){return failed;}
    public int remaining(){return route.size();}
    public Vec3 next(Vec3 start,Vec3 desired,int budget){
        Vec3 bounded=bound(start,desired);
        if(goal==null||goal.distanceToSqr(bounded)>36||origin!=null&&open.size()>0&&origin.distanceToSqr(start)>1024)
            begin(start,bounded,start.distanceToSqr(bounded)<=256?2:4);
        if(!route.isEmpty()){
            while(!route.isEmpty()&&start.distanceToSqr(route.peekFirst())<6.25)route.removeFirst();
            if(!route.isEmpty()&&!clearSegment(start,route.peekFirst()))begin(start,bounded,start.distanceToSqr(bounded)<=256?2:4);
        }
        if(route.isEmpty()&&clearSegment(start,bounded)){
            open.clear();best.clear();parent.clear();failed=false;
            return bounded;
        }
        if(!route.isEmpty())return smooth(start);
        for(int i=0;i<budget&&!open.isEmpty()&&explored<MAX_EXPLORED;i++){
            Node node=open.poll();
            if(node.cost>best.getOrDefault(node.cell,Double.POSITIVE_INFINITY)+1e-7)continue;
            explored++;
            Vec3 at=point(node.cell);
            if(at.distanceToSqr(goal)<36&&clearSegment(at,goal)){
                finish(node.cell);return smooth(start);
            }
            for(int[] move:MOVES){
                Cell next=new Cell(node.cell.x+move[0],node.cell.y+move[1],node.cell.z+move[2]);
                if(Math.abs(next.x)>MAX_HORIZONTAL||Math.abs(next.z)>MAX_HORIZONTAL||Math.abs(next.y)>MAX_VERTICAL)continue;
                Vec3 destination=point(next);
                if(!clearSegment(at,destination))continue;
                double cost=node.cost+at.distanceTo(destination)+(move[1]<0?2:move[1]>0?0.5:0);
                if(cost+1e-7>=best.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                best.put(next,cost);parent.put(next,node.cell);
                open.add(new Node(next,cost,cost+destination.distanceTo(goal)));
            }
        }
        if(open.isEmpty()||explored>=MAX_EXPLORED){
            if(step==4&&start.distanceToSqr(bounded)<=1156)begin(start,bounded,2);
            else{failed=true;open.clear();}
        }
        return null;
    }
    private Vec3 smooth(Vec3 start){
        Vec3 selected=route.peekFirst();
        int count=0,skip=0;
        for(Vec3 waypoint:route){
            if(++count>4||!clearSegment(start,waypoint))break;
            selected=waypoint;
            skip=count-1;
        }
        while(skip-->0)route.removeFirst();
        return selected;
    }
    private void begin(Vec3 start,Vec3 destination,int cellSize){
        reset();origin=start;goal=destination;step=cellSize;
        Cell first=new Cell(0,0,0);
        best.put(first,0.0);open.add(new Node(first,0,start.distanceTo(destination)));
    }
    private void finish(Cell last){
        ArrayDeque<Vec3> found=new ArrayDeque<>();
        for(Cell cell=last;cell!=null&&parent.containsKey(cell);cell=parent.get(cell))found.addFirst(point(cell));
        found.addLast(goal);
        route.clear();route.addAll(found);
        open.clear();best.clear();parent.clear();failed=false;
    }
    private Vec3 point(Cell cell){return origin.add(cell.x*step,cell.y*step,cell.z*step);}
    private boolean clearSegment(Vec3 from,Vec3 to){
        Vec3 delta=to.subtract(from);
        int samples=Math.max(1,(int)Math.ceil(delta.length()));
        for(int i=1;i<=samples;i++)if(!air.clear(from.add(delta.scale(i/(double)samples))))return false;
        return true;
    }
    private static Vec3 bound(Vec3 start,Vec3 goal){
        Vec3 difference=goal.subtract(start);
        return difference.lengthSqr()>4096?start.add(difference.normalize().scale(64)):goal;
    }
}
