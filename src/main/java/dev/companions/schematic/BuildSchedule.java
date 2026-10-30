package dev.companions.schematic;

import dev.companions.navigation.path.Cell;
import java.util.*;

/**
 * Shared, deterministic work queue for one schematic. Pure logic: the world
 * checks (support faces, reach) live in {@link SchematicJob}.
 *
 * Ordering rules:
 *  - Phases run strictly in order: STRUCTURE (walls, floors, roofs), then
 *    FITTINGS (ladders, doors, torches, beds ...), then FURNISHINGS (chests,
 *    crafting tables, furnaces and other workstations).
 *  - Inside a structure layer, work goes ring by ring: first the blocks that
 *    rest on something built below (for a roof, the ring over the walls), then
 *    each ring adjacent to the previous one, inward (and outward for eaves).
 *  - Inside a phase, work is offered from the lowest unfinished layer. A bot
 *    may look one layer ahead only where the block directly beneath is already
 *    finished, so nothing is built before its support and idle bots still work.
 *  - Failed attempts back off and are eventually skipped, so one impossible
 *    block can never hold the layer barrier (and every bot) forever. Before a
 *    layer is left behind, its skipped blocks get one more full round of
 *    attempts with the whole layer around them.
 */
public final class BuildSchedule {
    public enum Phase {STRUCTURE,FITTINGS,FURNISHINGS,DONE}
    public enum State {PENDING,CLAIMED,DONE,SKIPPED}
    /** stage is the phase ordinal the block belongs to (0 structure, 1 fittings, 2 furnishings). */
    public record Entry(Cell pos,int stage) {
        public Entry {if(stage<0||stage>=STAGES)throw new IllegalArgumentException("Unknown build stage "+stage);}
    }
    public static final int STAGES=3;
    public static final int MAX_ATTEMPTS=8;
    public static final int LOOKAHEAD=1;
    private final List<Entry> entries;
    private final State[] state;
    private final UUID[] owner;
    private final int[] attempts;
    private final long[] cooldown;
    private final Map<Cell,Integer> index=new HashMap<>();
    private final List<TreeMap<Integer,List<Integer>>> layers=new ArrayList<>();
    private final List<Map<Integer,Integer>> open=new ArrayList<>();
    private final int[] remaining=new int[STAGES];
    private final int[] ring;
    private final boolean[] secondPass;
    /** Per stage and layer, the number of unfinished entries in each ring. */
    private final List<Map<Integer,TreeMap<Integer,Integer>>> openRings=new ArrayList<>();
    private int done,skipped;

    public BuildSchedule(List<Entry> entries) {
        this.entries=List.copyOf(entries);
        for(int s=0;s<STAGES;s++){layers.add(new TreeMap<>());open.add(new HashMap<>());openRings.add(new HashMap<>());}
        int n=this.entries.size();
        state=new State[n];owner=new UUID[n];attempts=new int[n];cooldown=new long[n];ring=new int[n];secondPass=new boolean[n];
        Arrays.fill(state,State.PENDING);
        for(int i=0;i<n;i++){
            var e=this.entries.get(i);
            if(index.putIfAbsent(e.pos(),i)!=null)throw new IllegalArgumentException("Duplicate schematic cell "+e.pos());
            layers.get(e.stage()).computeIfAbsent(e.pos().y(),k->new ArrayList<>()).add(i);
        }
        computeRings();
        for(int i=0;i<n;i++)count(i,1);
    }
    /**
     * Breadth-first ring number inside each structure layer. Seeds are blocks
     * resting on a block of the schematic (or the lowest layer, which rests on
     * the ground); every further ring touches the previous one horizontally.
     * Blocks no ring reaches (floating islands) come after all rings.
     */
    private void computeRings(){
        Integer lowest=null;
        for(var e:entries)if(e.stage()==0&&(lowest==null||e.pos().y()<lowest))lowest=e.pos().y();
        for(var layer:layers.get(0).entrySet()){
            var cells=layer.getValue();
            var queue=new ArrayDeque<Integer>();
            for(int i:cells){
                ring[i]=-1;
                Integer below=index.get(entries.get(i).pos().add(0,-1,0));
                if(layer.getKey().equals(lowest)||below!=null&&entries.get(below).stage()==0){ring[i]=0;queue.add(i);}
            }
            if(queue.isEmpty()){for(int i:cells)ring[i]=0;continue;}
            int max=0;
            while(!queue.isEmpty()){
                int i=queue.poll();var p=entries.get(i).pos();
                for(var next:new Cell[]{p.add(1,0,0),p.add(-1,0,0),p.add(0,0,1),p.add(0,0,-1)}){
                    Integer j=index.get(next);
                    if(j==null||entries.get(j).stage()!=0||ring[j]>=0)continue;
                    ring[j]=ring[i]+1;max=Math.max(max,ring[j]);queue.add(j);
                }
            }
            for(int i:cells)if(ring[i]<0)ring[i]=max+1;
        }
    }
    public int ring(int i){return ring[i];}
    private int activeRing(int stage,int y){
        var rings=openRings.get(stage).get(y);
        if(rings==null)return 0;
        for(var e:rings.entrySet())if(e.getValue()>0)return e.getKey();
        return 0;
    }
    private void count(int i,int delta){
        var e=entries.get(i);
        open.get(e.stage()).merge(e.pos().y(),delta,Integer::sum);
        openRings.get(e.stage()).computeIfAbsent(e.pos().y(),k->new TreeMap<>()).merge(ring[i],delta,Integer::sum);
        remaining[e.stage()]+=delta;
    }
    public int size(){return entries.size();}
    public Entry entry(int i){return entries.get(i);}
    public State state(int i){return state[i];}
    public UUID owner(int i){return owner[i];}
    public int attempts(int i){return attempts[i];}
    public Integer indexOf(Cell pos){return index.get(pos);}
    public int done(){return done;}
    public int skipped(){return skipped;}
    public int remaining(){int n=0;for(int r:remaining)n+=r;return n;}
    public Phase phase(){for(int s=0;s<STAGES;s++)if(remaining[s]>0)return Phase.values()[s];return Phase.DONE;}
    /** Lowest layer of the current phase that still has unfinished entries, or null when done. */
    public Integer activeLayer(){
        Phase phase=phase();
        if(phase==Phase.DONE)return null;
        Integer lowest=null;
        for(var e:open.get(phase.ordinal()).entrySet())if(e.getValue()>0&&(lowest==null||e.getKey()<lowest))lowest=e.getKey();
        return lowest;
    }
    /** Entries a bot may claim now, lowest layer first. */
    public List<Integer> offered(long now){
        Phase phase=phase();Integer active=activeLayer();
        if(phase==Phase.DONE||active==null)return List.of();
        var out=new ArrayList<Integer>();
        var cooling=new ArrayList<Integer>();
        for(var layer:layers.get(phase.ordinal()).subMap(active,true,active+LOOKAHEAD,true).entrySet()){
            boolean ahead=layer.getKey()>active;
            int current=activeRing(phase.ordinal(),layer.getKey());
            for(int i:layer.getValue()){
                if(state[i]!=State.PENDING||ring[i]!=current)continue;
                if(ahead&&!supportedBelow(i))continue;
                (cooldown[i]>now?cooling:out).add(i);
            }
        }
        // Backoff only spreads retries between builders. When nothing else is left,
        // retry the soonest block now instead of idling: every retry still counts
        // toward MAX_ATTEMPTS, so an impossible block is skipped quickly.
        if(out.isEmpty()&&!cooling.isEmpty()){
            cooling.sort(Comparator.comparingLong(i->cooldown[i]));
            return cooling.subList(0,Math.min(cooling.size(),8));
        }
        return out;
    }
    private boolean supportedBelow(int i){
        Integer below=index.get(entries.get(i).pos().add(0,-1,0));
        return below==null||state[below]==State.DONE;
    }
    public boolean claim(int i,UUID bot){
        if(state[i]!=State.PENDING)return false;
        state[i]=State.CLAIMED;owner[i]=bot;return true;
    }
    /** Returns the claim. A failed attempt backs off; repeated failures skip the entry. */
    public void release(int i,long now,boolean failed){
        if(state[i]!=State.CLAIMED)return;
        state[i]=State.PENDING;owner[i]=null;
        if(failed){
            attempts[i]++;
            cooldown[i]=now+Math.min(200,20L<<Math.min(attempts[i],4));
            if(attempts[i]>=MAX_ATTEMPTS)close(i,State.SKIPPED);
        }
    }
    /** Returns the claim without counting a failure, e.g. when an entity briefly stands in the cell. */
    public void defer(int i,long until){
        if(state[i]!=State.CLAIMED)return;
        state[i]=State.PENDING;owner[i]=null;cooldown[i]=Math.max(cooldown[i],until);
    }
    /** Gives every skipped entry one more chance once the rest is finished. */
    public int revive(){
        int n=0;
        for(int i=0;i<state.length;i++)if(state[i]==State.SKIPPED){
            state[i]=State.PENDING;attempts[i]=0;cooldown[i]=0;skipped--;n++;
            count(i,1);
        }
        return n;
    }
    public void releaseAll(UUID bot,long now){for(int i=0;i<owner.length;i++)if(bot.equals(owner[i]))release(i,now,false);}
    public void complete(int i){if(state[i]==State.PENDING||state[i]==State.CLAIMED)close(i,State.DONE);}
    public void skip(int i){if(state[i]==State.PENDING||state[i]==State.CLAIMED)close(i,State.SKIPPED);}
    /** A finished cell that the world no longer matches (e.g. a mob broke it) goes back into the queue. */
    public void reopen(int i){
        if(state[i]!=State.DONE)return;
        state[i]=State.PENDING;done--;attempts[i]=0;cooldown[i]=0;
        count(i,1);
    }
    private void close(int i,State result){
        state[i]=result;owner[i]=null;
        count(i,-1);
        if(result==State.DONE)done++;else skipped++;
        var e=entries.get(i);
        if(open.get(e.stage()).getOrDefault(e.pos().y(),0)==0)secondPass(e.stage(),e.pos().y());
    }
    /** The layer has nothing open left: give each block skipped in it one more round before moving up. */
    private void secondPass(int stage,int y){
        for(int i:layers.get(stage).getOrDefault(y,List.of())){
            if(state[i]!=State.SKIPPED||secondPass[i])continue;
            secondPass[i]=true;state[i]=State.PENDING;attempts[i]=0;cooldown[i]=0;skipped--;
            count(i,1);
        }
    }
    /** Whether the block is already on its second round (used to escalate how it is placed). */
    public boolean onSecondPass(int i){return secondPass[i];}
}
