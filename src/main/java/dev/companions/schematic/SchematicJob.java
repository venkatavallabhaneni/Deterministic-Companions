package dev.companions.schematic;

import dev.companions.navigation.path.Cell;
import dev.companions.player.CompanionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Consumer;

/**
 * One schematic build shared by every participating companion. All state is
 * touched only on the server thread from the builders' task ticks.
 *
 * Anti-stall guarantees: claims expire when a builder stops touching them,
 * failed blocks back off and are eventually skipped, scaffolds that cannot be
 * removed are reported instead of retried forever, and a job-wide watchdog
 * releases every claim when nothing has progressed for a minute.
 */
public final class SchematicJob {
    public enum Stage {CLEARING,PLANNING,BUILDING,CLEANUP,DONE,STOPPED}
    public static final int CLAIM_TIMEOUT=200,STALL_LIMIT=1200,SCAFFOLD_ATTEMPTS=6,MAX_REOPEN=3,BREACH_TICKS=600,BREACH_COST=40,MAX_SEALS=2;
    private static final List<Block> SCAFFOLD_CHOICES=List.of(Blocks.DIRT,Blocks.NETHERRACK,Blocks.COBBLED_DEEPSLATE,Blocks.COBBLESTONE,Blocks.ANDESITE,Blocks.DIORITE,Blocks.GRANITE,Blocks.TUFF,Blocks.END_STONE,Blocks.STONE);
    private static final Set<Block> GRASSY=Set.of(Blocks.GRASS_BLOCK,Blocks.MYCELIUM,Blocks.PODZOL,Blocks.DIRT_PATH,Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT);

    public final ServerLevel level;
    public final SchematicPlan plan;
    public final BuildSchedule schedule;
    public final Block scaffoldBlock;
    public final Item scaffoldItem;
    public final String scaffoldId;
    private final List<CompanionController> builders=new ArrayList<>();
    private final Map<BlockPos,Integer> partners=new HashMap<>();
    private final Map<Integer,Long> touched=new HashMap<>();
    private final Map<UUID,BlockPos> stands=new HashMap<>();
    /** Each builder's stand, the head space above it and the pillar column it will climb. */
    private final Map<UUID,Set<BlockPos>> reserved=new HashMap<>();
    private final Map<UUID,Long> breach=new HashMap<>();
    private final Map<Item,Integer> outstanding=new HashMap<>();
    private final Map<Integer,Integer> reopened=new HashMap<>();
    private final ReachTracker reach=new ReachTracker();
    private final Set<String> benchAnnounced=new HashSet<>();
    /** Finished blocks broken to get through, re-placed ("sealed") once everything else is built. */
    private final Set<Integer> breaches=new LinkedHashSet<>();
    private final Map<Integer,Integer> seals=new HashMap<>();
    private final Set<Integer> sealing=new HashSet<>();
    private record Scaffold(int attempts,long cooldown,UUID owner,long touched) {}
    private final LinkedHashMap<BlockPos,Scaffold> scaffolds=new LinkedHashMap<>();
    private final List<BlockPos> leftovers=new ArrayList<>();
    /** Pre-build clearing: the footprint plus CLEAR_MARGIN around it, from the build's base to CLEAR_ABOVE over its top. */
    public static final int CLEAR_MARGIN=2,CLEAR_ABOVE=2,SCAN_PER_TICK=4096;
    private final LinkedHashMap<BlockPos,Scaffold> clearing=new LinkedHashMap<>();
    private final BlockPos clearMin,clearMax;
    private final long scanTotal;
    private long scanCursor;
    private int cleared,kept;
    /** Pre-build plan: a snapshot of the cleared site, planned off-thread, then followed by the builders. */
    public static final int PLAN_MARGIN=6;
    private VoxelModel model;
    private BlockPos snapMin,snapMax;
    private long snapCursor,snapTotal;
    private java.util.concurrent.CompletableFuture<BuildPlanner.Result> planning;
    private BuildPlanner.Result planned;
    private final Consumer<String> report;
    private Stage stage=Stage.CLEARING;
    private long lastTick=-1,lastProgress,startTick;
    private int sweep,forced,stalls,scaffoldTotal;
    private Integer announcedLayer;
    private BuildSchedule.Phase announcedPhase;
    private boolean revived;
    private final ArrayDeque<String> skips=new ArrayDeque<>();
    private long lastSkipReport=-1000;
    private int skipReports;

    public SchematicJob(ServerLevel level,SchematicPlan plan,List<CompanionController> participants,Consumer<String> report){
        this.level=level;this.plan=plan;this.report=report;
        var entries=new ArrayList<BuildSchedule.Entry>(plan.placements().size());
        for(var p:plan.placements())entries.add(new BuildSchedule.Entry(new Cell(p.pos().getX(),p.pos().getY(),p.pos().getZ()),p.stage()));
        schedule=new BuildSchedule(entries);
        for(int i=0;i<plan.placements().size();i++){
            var p=plan.placements().get(i);
            if(p.partner()!=null)partners.put(p.partner(),i);
            outstanding.merge(p.item(),p.count(),Integer::sum);
        }
        var used=new HashSet<Block>();for(var p:plan.placements())used.add(p.state().getBlock());
        Block chosen=Blocks.DIRT;
        for(Block b:SCAFFOLD_CHOICES){
            if(used.contains(b)||b==Blocks.DIRT&&used.stream().anyMatch(GRASSY::contains))continue;
            chosen=b;break;
        }
        scaffoldBlock=chosen;scaffoldItem=chosen.asItem();scaffoldId=BuiltInRegistries.ITEM.getKey(scaffoldItem).toString();
        var ordered=new ArrayList<>(participants);
        ordered.sort(Comparator.comparing(c->c.player.getPlainTextName().toLowerCase(Locale.ROOT)));
        builders.addAll(ordered);
        startTick=lastProgress=level.getGameTime();
        clearMin=new BlockPos(plan.min().getX()-CLEAR_MARGIN,plan.min().getY(),plan.min().getZ()-CLEAR_MARGIN);
        clearMax=new BlockPos(plan.max().getX()+CLEAR_MARGIN,Math.min(level.getMaxY(),plan.max().getY()+CLEAR_ABOVE),plan.max().getZ()+CLEAR_MARGIN);
        scanTotal=(long)(clearMax.getX()-clearMin.getX()+1)*(clearMax.getY()-clearMin.getY()+1)*(clearMax.getZ()-clearMin.getZ()+1);
    }

    public Stage stage(){return stage;}
    public List<CompanionController> builders(){return List.copyOf(builders);}
    public SchematicPlan.Placement placement(int i){return plan.placements().get(i);}
    public boolean scaffoldLike(BlockState s){return s.is(scaffoldBlock)||scaffoldBlock==Blocks.DIRT&&GRASSY.contains(s.getBlock());}
    public void stop(){if(stage==Stage.DONE||stage==Stage.STOPPED)return;stage=Stage.STOPPED;report.accept("Schematic "+plan.name()+" stopped. "+progress());}
    public void leave(CompanionController c){
        builders.remove(c);stands.remove(c.player.getUUID());reserved.remove(c.player.getUUID());reach.forget(c.player.getUUID());
        schedule.releaseAll(c.player.getUUID(),level.getGameTime());
        touched.keySet().removeIf(i->schedule.state(i)!=BuildSchedule.State.CLAIMED);
        scaffolds.replaceAll((p,s)->c.player.getUUID().equals(s.owner)?new Scaffold(s.attempts,s.cooldown,null,0):s);
        clearing.replaceAll((p,s)->c.player.getUUID().equals(s.owner)?new Scaffold(s.attempts,s.cooldown,null,0):s);
        if(builders.isEmpty()&&stage!=Stage.DONE&&stage!=Stage.STOPPED){stage=Stage.STOPPED;report.accept("Schematic "+plan.name()+" paused: no builders remain. "+progress());}
    }
    public void progressed(){lastProgress=level.getGameTime();}

    /** Runs once per server tick no matter how many builders call it. */
    public void tick(){
        long now=level.getGameTime();
        if(now==lastTick||stage==Stage.DONE||stage==Stage.STOPPED)return;
        lastTick=now;
        // Expire claims whose builder stopped working on them (death, emergency, removal).
        for(var it=touched.entrySet().iterator();it.hasNext();){
            var e=it.next();
            if(schedule.state(e.getKey())!=BuildSchedule.State.CLAIMED){it.remove();continue;}
            // The builder stopped touching its claim (eating, fighting, removed): not the block's fault.
            if(now-e.getValue()>CLAIM_TIMEOUT){schedule.release(e.getKey(),now,false);it.remove();}
        }
        scaffolds.replaceAll((p,s)->s.owner!=null&&now-s.touched>CLAIM_TIMEOUT?new Scaffold(s.attempts,now+40,null,0):s);
        clearing.replaceAll((p,s)->s.owner!=null&&now-s.touched>CLAIM_TIMEOUT?new Scaffold(s.attempts,now+40,null,0):s);
        breach.values().removeIf(until->until<now);
        if(stage==Stage.CLEARING){
            boolean scanned=scanCursor>=scanTotal;
            scanSlice();
            if(!scanned&&scanCursor>=scanTotal)
                report.accept(plan.name()+": clearing "+clearing.size()+" block(s) in the build area and "+CLEAR_MARGIN+" blocks around it before building"
                    +(kept>0?" ("+kept+" protected block(s) such as chests are left in place)":""));
            if(scanCursor>=scanTotal&&clearing.isEmpty()){
                stage=Stage.PLANNING;progressed();
                report.accept(plan.name()+": area clear"+(cleared>0?" ("+cleared+" block(s) removed)":"")+"; planning the build");
            }else{watchdog(now);return;}
        }
        if(stage==Stage.PLANNING){planTick();return;}
        verifySlice();
        if(schedule.phase()!=BuildSchedule.Phase.DONE){
            stage=Stage.BUILDING;
            Integer layer=schedule.activeLayer();
            if(layer!=null&&(!layer.equals(announcedLayer)||schedule.phase()!=announcedPhase)){
                var phase=schedule.phase();
                if(phase!=announcedPhase){
                    report.accept(plan.name()+": "+switch(phase){
                        case STRUCTURE->"building the structure";
                        case FITTINGS->"placing ladders, doors, torches and other fittings";
                        default->"placing chests, crafting tables, furnaces and other furnishings";
                    }+" from layer "+(layer-plan.min().getY()+1));
                }else if(announcedLayer!=null&&layer>announcedLayer)
                    report.accept(plan.name()+": "+phase.name().toLowerCase(Locale.ROOT)+" layer "+(layer-plan.min().getY()+1)+"/"+(plan.max().getY()-plan.min().getY()+1)+" — "+progress());
                announcedLayer=layer;announcedPhase=phase;
            }
        }else{
            if(!breaches.isEmpty()){
                // Seal every gap that was broken to get through, with the block the schematic wants there.
                int n=0;
                for(int i:breaches){
                    var p=placement(i);
                    if(schedule.state(i)!=BuildSchedule.State.DONE||!level.hasChunkAt(p.pos())||PlacementRules.matches(level.getBlockState(p.pos()),p.state()))continue;
                    schedule.reopen(i);outstanding.merge(p.item(),p.count(),Integer::sum);sealing.add(i);n++;
                }
                breaches.clear();
                if(n>0){report.accept(plan.name()+": sealing "+n+" block(s) broken to get through");return;}
            }
            if(!revived&&schedule.skipped()>0){
                // Transient failures (mobs, other builders, routes) get one final pass.
                revived=true;int n=schedule.revive();
                for(int i=0;i<schedule.size();i++)if(schedule.state(i)==BuildSchedule.State.PENDING){var p=placement(i);outstanding.merge(p.item(),p.count(),Integer::sum);}
                report.accept(plan.name()+": retrying "+n+" previously skipped block(s)");
                return;
            }
            if(stage==Stage.BUILDING){stage=Stage.CLEANUP;report.accept(plan.name()+": all blocks placed; removing "+scaffolds.size()+" temporary scaffold blocks");}
            if(scaffolds.isEmpty()){
                stage=Stage.DONE;
                report.accept("Schematic "+plan.name()+" complete in "+(now-startTick)/20+"s. "+progress()+(leftovers.isEmpty()?"":"; "+leftovers.size()+" scaffold block(s) could not be removed, first at "+leftovers.getFirst().toShortString()));
                return;
            }
        }
        watchdog(now);
    }
    private void watchdog(long now){
        if(now-lastProgress<=STALL_LIMIT)return;
        // Global watchdog: nothing was placed or removed for a minute.
        stalls++;lastProgress=now;
        for(int i=0;i<schedule.size();i++)if(schedule.state(i)==BuildSchedule.State.CLAIMED)schedule.release(i,now,false);
        touched.clear();stands.clear();reserved.clear();
        // Whoever is still stuck after a minute may break through.
        for(var b:builders)allowBreach(b);
        scaffolds.replaceAll((p,s)->new Scaffold(s.attempts+1,0,null,0));
        clearing.replaceAll((p,s)->new Scaffold(s.attempts+1,0,null,0));
        for(var b:builders)b.navigation.stop();
        report.accept(plan.name()+": no progress for "+STALL_LIMIT/20+"s — released all work and replanning (recovery "+stalls+")");
    }
    /** Finds blocks to clear, top layer first, a bounded slice per tick so large areas never stall the server. */
    private void scanSlice(){
        for(int n=0;n<SCAN_PER_TICK&&scanCursor<scanTotal;n++,scanCursor++){
            BlockPos pos=scanCell(clearMin,clearMax,scanCursor);
            if(!level.hasChunkAt(pos))continue;
            BlockState state=level.getBlockState(pos);
            if(!needsClearing(pos,state))continue;
            // Clearing follows the same explicit-dig safety rules as /bot dig: containers, beds, spawners, bedrock ... stay.
            if(dev.companions.world.BlockSafetyPolicy.mayBreak(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),state.hasBlockEntity(),state.getDestroySpeed(level,pos),true))
                clearing.put(pos,new Scaffold(0,0,null,0));
            else kept++;
        }
    }
    /**
     * PLANNING: snapshot the site a slice per tick (solid cells and hazards around the footprint), then
     * run {@link BuildPlanner} on a background thread and apply its result. Construction starts with or
     * without a plan; a failed plan only means builders search live for every block.
     */
    private void planTick(){
        if(model==null){
            snapMin=new BlockPos(plan.min().getX()-PLAN_MARGIN,plan.min().getY()-PLAN_MARGIN,plan.min().getZ()-PLAN_MARGIN);
            snapMax=new BlockPos(plan.max().getX()+PLAN_MARGIN,Math.min(level.getMaxY(),plan.max().getY()+3),plan.max().getZ()+PLAN_MARGIN);
            snapTotal=(long)(snapMax.getX()-snapMin.getX()+1)*(snapMax.getY()-snapMin.getY()+1)*(snapMax.getZ()-snapMin.getZ()+1);
            model=new VoxelModel(snapMin,snapMax);
        }
        if(snapCursor<snapTotal){
            for(int n=0;n<SCAN_PER_TICK&&snapCursor<snapTotal;n++,snapCursor++){
                BlockPos p=scanCell(snapMin,snapMax,snapCursor);
                if(!level.hasChunkAt(p)){model.setHazard(p);continue;}
                var state=level.getBlockState(p);
                if(!state.getCollisionShape(level,p).isEmpty())model.setSolid(p,true);
                if(dev.companions.world.BlockSafetyPolicy.dangerous(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())||state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))model.setHazard(p);
            }
            return;
        }
        if(planning==null){
            var blocks=new ArrayList<BuildPlanner.Block>(schedule.size());
            for(int i=0;i<schedule.size();i++){
                var p=placement(i);
                boolean solid=!p.state().getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty();
                blocks.add(new BuildPlanner.Block(p.pos(),solid,PlacementRules.supportOrder(p.state()),p.stage(),schedule.ring(i)));
            }
            var snapshot=model;BlockPos min=plan.min(),max=plan.max();
            planning=java.util.concurrent.CompletableFuture.supplyAsync(()->BuildPlanner.plan(snapshot,blocks,min,max,1.62));
            return;
        }
        if(!planning.isDone())return;
        try{
            planned=planning.join();
            report.accept(plan.name()+": plan ready — "+schedule.size()+" blocks: "+planned.summary()+"; starting construction");
        }catch(RuntimeException e){
            planned=null;
            org.slf4j.LoggerFactory.getLogger("companions").warn("Schematic planning failed",e);
            report.accept(plan.name()+": planning failed ("+e.getMessage()+"); building without a plan");
        }
        model=null;stage=Stage.BUILDING;progressed();
    }
    /** Planned stand for a block, or null (no plan, or no spot was found while planning). */
    public BlockPos plannedStand(int i){var s=planned==null?null:planned.steps().get(i);return s==null?null:s.stand();}
    /** The plan found no clickable face for this block: place it by aiming at the cell (set directly if needed). */
    public boolean plannedDirect(int i){var s=planned==null?null:planned.steps().get(i);return s!=null&&s.direct();}
    public String planReport(){
        if(planned==null)return stage.ordinal()<=Stage.PLANNING.ordinal()?"The build is still being planned":"This build has no plan; builders search live";
        var text=new StringBuilder(plan.name()+": "+schedule.size()+" blocks — "+planned.summary());
        int shown=0;
        for(var t:planned.towers()){if(shown++==8){text.append("\n  …");break;}text.append("\n  scaffold tower at ").append(t.toShortString());}
        return text.toString();
    }
    /** The cursor-th cell of a box, walking each horizontal layer from the top layer down. */
    public static BlockPos scanCell(BlockPos min,BlockPos max,long cursor){
        int sx=max.getX()-min.getX()+1,sz=max.getZ()-min.getZ()+1;
        long plane=(long)sx*sz;
        int rest=(int)(cursor%plane);
        return new BlockPos(min.getX()+rest%sx,max.getY()-(int)(cursor/plane),min.getZ()+rest/sx);
    }
    /**
     * A block in the clearing area that is in the way: anything solid or
     * otherwise not replaceable, except fluids and blocks the schematic already
     * wants exactly there (same block; orientation is fixed during the build).
     */
    public boolean needsClearing(BlockPos pos,BlockState state){
        if(state.isAir()||state.canBeReplaced()||state.getBlock() instanceof LiquidBlock)return false;
        Integer i=schedule.indexOf(new Cell(pos.getX(),pos.getY(),pos.getZ()));
        if(i==null)i=partners.get(pos);
        if(i!=null&&placement(i).state().getBlock()==state.getBlock())return false;
        return !scaffolds.containsKey(pos);
    }
    public boolean inClearingArea(BlockPos p){
        return p.getX()>=clearMin.getX()&&p.getX()<=clearMax.getX()&&p.getY()>=clearMin.getY()&&p.getY()<=clearMax.getY()&&p.getZ()>=clearMin.getZ()&&p.getZ()<=clearMax.getZ();
    }
    /** Re-queues finished blocks that disappeared (mob, explosion, breach); bounded per block. */
    private void verifySlice(){
        if(schedule.size()==0)return;
        for(int n=0;n<32;n++){
            int i=sweep++%schedule.size();
            if(schedule.state(i)!=BuildSchedule.State.DONE||breaches.contains(i))continue;
            var p=placement(i);
            if(!level.hasChunkAt(p.pos()))continue;
            var actual=level.getBlockState(p.pos());
            if(!actual.canBeReplaced()||PlacementRules.matches(actual,p.state()))continue;
            if(reopened.merge(i,1,Integer::sum)>MAX_REOPEN)continue;
            schedule.reopen(i);outstanding.merge(p.item(),p.count(),Integer::sum);
        }
    }

    // ---- block work -------------------------------------------------------
    public Integer claimNext(CompanionController c){
        long now=level.getGameTime();
        if(benchNote(c)!=null)return null;
        var offered=schedule.offered(now);
        if(offered.isEmpty())return null;
        int sector=builders.indexOf(c),count=Math.max(1,builders.size());
        boolean xAxis=plan.max().getX()-plan.min().getX()>=plan.max().getZ()-plan.min().getZ();
        Integer active=schedule.activeLayer();
        Integer best=null;double bestScore=Double.POSITIVE_INFINITY;
        int scanned=0;
        var others=new ArrayList<BlockPos>();
        for(int i:touched.keySet())if(!c.player.getUUID().equals(schedule.owner(i)))others.add(placement(i).pos());
        for(var b:builders)if(b!=c)others.add(b.player.blockPosition());
        for(int i:offered){
            if(++scanned>4096)break;
            var pos=placement(i).pos();
            if(othersStandOn(c,pos))continue;
            double score=pos.distToCenterSqr(c.player.position());
            // A block with no solid neighbour yet cannot be clicked into place. Take it only
            // when nothing placeable is left in the layer (it then gets a support scaffold),
            // and never one layer ahead.
            if(!attached(pos)){if(active!=null&&pos.getY()>active)continue;score+=100000;}
            if(sector>=0&&WorkSplit.sector(xAxis?pos.getX():pos.getZ(),xAxis?plan.min().getX():plan.min().getZ(),xAxis?plan.max().getX():plan.max().getZ(),count)!=sector)score+=96;
            if(active!=null)score+=(pos.getY()-active)*48;
            // Following the plan: blocks placeable from right where this builder stands come first.
            BlockPos ps=plannedStand(i);
            if(ps!=null){
                BlockPos feet=c.player.blockPosition();
                if(Math.abs(ps.getX()-feet.getX())<=1&&Math.abs(ps.getZ()-feet.getZ())<=1&&Math.abs(ps.getY()-feet.getY())<=1)score-=400;
                if(standTaken(c,ps))score+=200;
            }
            // Spread out: blocks right next to another builder's block or body mean pushing and shared stands.
            for(var o:others)if(Math.abs(o.getX()-pos.getX())<=2&&Math.abs(o.getZ()-pos.getZ())<=2&&Math.abs(o.getY()-pos.getY())<=3){score+=150;break;}
            if(score<bestScore){bestScore=score;best=i;}
        }
        if(best==null||!schedule.claim(best,c.player.getUUID()))return null;
        touched.put(best,now);
        return best;
    }
    private boolean othersStandOn(CompanionController c,BlockPos pos){
        for(var e:stands.entrySet())if(!e.getKey().equals(c.player.getUUID())&&(e.getValue().equals(pos)||e.getValue().above().equals(pos)))return true;
        for(var b:builders)if(b!=c&&b.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos)))return true;
        return false;
    }
    /**
     * Non-null while this builder is benched for the current layer: it kept failing to get there while
     * another builder is placing blocks on it, so that builder does the layer alone for now.
     */
    public String benchNote(CompanionController c){
        if(stage!=Stage.BUILDING)return null;
        Integer active=schedule.activeLayer();
        if(active==null)return null;
        int phase=schedule.phase().ordinal();
        var ids=new ArrayList<UUID>();for(var b:builders)ids.add(b.player.getUUID());
        if(!reach.benched(c.player.getUUID(),phase,active,level.getGameTime(),ids))return null;
        var names=new ArrayList<String>();
        for(var b:builders)if(reach.reaching(phase,active,List.of(b.player.getUUID())).size()>0)names.add(b.player.getPlainTextName());
        String note="only "+String.join(", ",names)+" can reach layer "+(active-plan.min().getY()+1)+"; waiting";
        if(benchAnnounced.add(phase+":"+active+":"+c.player.getUUID()))report.accept(plan.name()+": "+c.player.getPlainTextName()+" can't reach layer "+(active-plan.min().getY()+1)+"; leaving it to "+String.join(", ",names));
        return note;
    }
    public boolean attached(BlockPos pos){
        for(var d:net.minecraft.core.Direction.values()){
            BlockPos n=pos.relative(d);
            if(level.hasChunkAt(n)&&!level.getBlockState(n).getCollisionShape(level,n).isEmpty())return true;
        }
        return false;
    }
    /** Cost of a builder occupying a cell: blocks of the current layer must stay free, later ones only cost a detour. */
    public double standCost(BlockPos p){
        Integer i=schedule.indexOf(new Cell(p.getX(),p.getY(),p.getZ()));
        if(i==null){Integer partner=partners.get(p);if(partner==null)return 0;i=partner;}
        var state=schedule.state(i);
        if(state==BuildSchedule.State.DONE||state==BuildSchedule.State.SKIPPED)return 0;
        if(schedule.entry(i).stage()!=schedule.phase().ordinal())return 2;
        Integer active=schedule.activeLayer();
        return active!=null&&p.getY()==active?12:4;
    }
    public void touch(int i){touched.put(i,level.getGameTime());}
    public boolean owns(int i,CompanionController c){return schedule.state(i)==BuildSchedule.State.CLAIMED&&c.player.getUUID().equals(schedule.owner(i));}
    public void complete(int i,boolean force){
        var p=placement(i);
        sealing.remove(i);
        UUID owner=schedule.owner(i);
        if(owner!=null)reach.reached(owner,schedule.entry(i).stage(),p.pos().getY());
        if(schedule.state(i)==BuildSchedule.State.PENDING||schedule.state(i)==BuildSchedule.State.CLAIMED){
            schedule.complete(i);outstanding.merge(p.item(),-p.count(),Integer::sum);
        }
        touched.remove(i);scaffolds.remove(p.pos());if(p.partner()!=null)scaffolds.remove(p.partner());
        if(force)forced++;
        progressed();
    }
    /** A failed attempt. When it was the block's last, the skip and its reason are reported. */
    public void fail(int i,String why){fail(i,why,false);}
    /** @param unreachable the builder could not get to the block (no spot, no route, timed out, stuck) */
    public void fail(int i,String why,boolean unreachable){
        UUID owner=schedule.owner(i);
        if(unreachable&&owner!=null)reach.missed(owner,schedule.entry(i).stage(),placement(i).pos().getY());
        schedule.release(i,level.getGameTime(),true);touched.remove(i);
        if(schedule.state(i)!=BuildSchedule.State.SKIPPED)return;
        var p=placement(i);
        String line=BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).getPath()+" @ "+p.pos().toShortString()+": "+why+(schedule.onSecondPass(i)?" (final)":" (will retry once the layer is done)");
        skips.addFirst(line);while(skips.size()>5)skips.removeLast();
        skipReports++;
        long now=level.getGameTime();
        if(now-lastSkipReport>=100){lastSkipReport=now;report.accept(plan.name()+": skipped "+line+(skipReports>1?" (+"+(skipReports-1)+" more)":""));skipReports=0;}
    }
    public void release(int i){schedule.release(i,level.getGameTime(),false);touched.remove(i);}
    public void defer(int i,int ticks){schedule.defer(i,level.getGameTime()+ticks);touched.remove(i);}
    public void skip(int i){
        var p=placement(i);
        if(schedule.state(i)==BuildSchedule.State.PENDING||schedule.state(i)==BuildSchedule.State.CLAIMED)outstanding.merge(p.item(),-p.count(),Integer::sum);
        schedule.skip(i);touched.remove(i);
    }
    /** Builders' bodies must not stand in a cell that still has to receive a block soon. */
    public boolean pendingNear(BlockPos p){
        Integer i=schedule.indexOf(new Cell(p.getX(),p.getY(),p.getZ()));
        if(i==null){Integer partner=partners.get(p);if(partner==null)return false;i=partner;}
        var state=schedule.state(i);
        if(state==BuildSchedule.State.DONE||state==BuildSchedule.State.SKIPPED)return false;
        Integer active=schedule.activeLayer();
        boolean phase=schedule.entry(i).stage()==schedule.phase().ordinal();
        return phase&&active!=null&&p.getY()<=active+BuildSchedule.LOOKAHEAD+1;
    }
    /** Finished schematic blocks may not be dug by navigation, except during a builder's escape breach. */
    public boolean protects(CompanionController c,BlockPos p){
        if(!plan.contains(p))return false;
        Long until=breach.get(c.player.getUUID());
        if(until!=null&&until>=level.getGameTime())return false;
        Integer i=schedule.indexOf(new Cell(p.getX(),p.getY(),p.getZ()));
        if(i==null)i=partners.get(p);
        return i!=null&&schedule.state(i)==BuildSchedule.State.DONE;
    }
    /**
     * Lets one builder break through finished blocks for a while: when no spot it can reach by walking
     * or scaffolding around works, or it is walled in. Routes still prefer going around (see breakCost).
     */
    public void allowBreach(CompanionController c){breach.put(c.player.getUUID(),level.getGameTime()+BREACH_TICKS);}
    public boolean breaching(CompanionController c){Long until=breach.get(c.player.getUUID());return until!=null&&until>=level.getGameTime();}
    private Integer finishedAt(BlockPos p){
        if(!plan.contains(p))return null;
        Integer i=schedule.indexOf(new Cell(p.getX(),p.getY(),p.getZ()));
        if(i==null)i=partners.get(p);
        return i!=null&&schedule.state(i)==BuildSchedule.State.DONE?i:null;
    }
    /** Route cost of breaking a block: a finished schematic block is a last resort, scaffolding around is preferred. */
    public double breakCost(Cell p){return finishedAt(new BlockPos(p.x(),p.y(),p.z()))!=null?BREACH_COST:0;}
    /**
     * Called for every block a builder breaks. A finished block broken while building is a breach:
     * it stays open as a passage and is sealed at the end. During scaffold removal, or once a block
     * has been sealed MAX_SEALS times, it is simply rebuilt by verification instead.
     */
    public void broken(BlockPos p){
        Integer i=finishedAt(p);
        if(i==null)return;
        if(stage==Stage.CLEANUP||seals.merge(i,1,Integer::sum)>MAX_SEALS)return;
        breaches.add(i);
        report.accept(plan.name()+": broke through "+BuiltInRegistries.BLOCK.getKey(placement(i).state().getBlock()).getPath()+" @ "+p.toShortString()+" to get past; it will be sealed at the end");
    }
    /** Sealing a gap is done from outside the footprint when possible, so nobody walls themselves in. */
    public boolean sealing(int i){return sealing.contains(i);}
    public void reserveStand(CompanionController c,BlockPos p){
        UUID id=c.player.getUUID();
        if(p==null){stands.remove(id);reserved.remove(id);return;}
        stands.put(id,p.immutable());
        var cells=new HashSet<BlockPos>();
        cells.add(p.immutable());cells.add(p.above().immutable());
        // The pillar this builder will raise to reach the stand belongs to it too.
        for(int n=1;n<=StandFinder.MAX_PILLAR;n++){
            BlockPos below=p.below(n);
            if(!level.hasChunkAt(below)||!level.getBlockState(below).canBeReplaced())break;
            cells.add(below.immutable());
        }
        reserved.put(id,cells);
    }
    public boolean standTaken(CompanionController c,BlockPos p){
        for(var e:reserved.entrySet())if(!e.getKey().equals(c.player.getUUID())&&(e.getValue().contains(p)||e.getValue().contains(p.below())))return true;
        return false;
    }
    /**
     * Route cost for one builder entering a cell: another builder's body or
     * reserved stand/pillar column is a detour, so routes and scaffolding stay
     * out of each other's way whenever an alternative exists.
     */
    public double crowdCost(CompanionController c,Cell p){
        BlockPos pos=new BlockPos(p.x(),p.y(),p.z());
        for(var e:reserved.entrySet())if(!e.getKey().equals(c.player.getUUID())&&e.getValue().contains(pos))return 30;
        for(var b:builders){
            if(b==c)continue;
            BlockPos feet=b.player.blockPosition();
            if(feet.getX()==p.x()&&feet.getZ()==p.z()&&(feet.getY()==p.y()||feet.getY()+1==p.y()||feet.getY()-1==p.y()))return 25;
        }
        return 0;
    }
    /**
     * Route cost for a temporary pillar/bridge block: another builder's reserved cells, and any cell the
     * build has to fill in the current or next layer (scaffold there must be dug out again, and digging
     * it out from on top of it is exactly how builders got stranded).
     */
    public double placementCost(CompanionController c,Cell p){
        double cost=crowdCost(c,p);
        Integer i=schedule.indexOf(p);
        if(i==null)i=partners.get(new BlockPos(p.x(),p.y(),p.z()));
        if(i==null)return cost;
        var state=schedule.state(i);
        Integer active=schedule.activeLayer();
        if((state==BuildSchedule.State.PENDING||state==BuildSchedule.State.CLAIMED)&&active!=null&&p.y()<=active+1)cost+=40;
        return cost;
    }
    /** Another builder stands somewhere on the column above this cell (it is their footing). */
    public boolean supportsOther(CompanionController c,BlockPos p){
        for(var b:builders){
            if(b==c)continue;
            BlockPos feet=b.player.blockPosition();
            if(feet.getX()==p.getX()&&feet.getZ()==p.getZ()&&feet.getY()>p.getY()&&feet.getY()-p.getY()<=StandFinder.MAX_PILLAR+2)return true;
        }
        return false;
    }

    // ---- materials ------------------------------------------------------------
    /** Grants just enough of an item, accounting for what every builder already holds. */
    public int supplyAmount(Item item){
        String id=BuiltInRegistries.ITEM.getKey(item).toString();
        int held=0;for(var b:builders)held+=b.inventory.count(id);
        int need=outstanding.getOrDefault(item,0)-held;
        return Math.max(1,Math.min(item.getDefaultMaxStackSize(),need));
    }
    public boolean needed(Item item){return outstanding.getOrDefault(item,0)>0;}

    // ---- scaffolding -----------------------------------------------------------
    public void recordScaffold(BlockPos p){
        Integer i=schedule.indexOf(new Cell(p.getX(),p.getY(),p.getZ()));
        if(i!=null&&schedule.state(i)==BuildSchedule.State.DONE)return;
        if(scaffolds.putIfAbsent(p.immutable(),new Scaffold(0,0,null,0))==null)scaffoldTotal++;
    }
    public boolean isScaffold(BlockPos p){return scaffolds.containsKey(p);}
    public BlockPos claimScaffold(CompanionController c){return claimRemoval(scaffolds,c);}
    public BlockPos claimClear(CompanionController c){return claimRemoval(clearing,c);}
    private BlockPos claimRemoval(LinkedHashMap<BlockPos,Scaffold> list,CompanionController c){
        long now=level.getGameTime();
        BlockPos best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(var e:list.entrySet()){
            var s=e.getValue();
            if(s.owner!=null&&!s.owner.equals(c.player.getUUID())||s.cooldown>now)continue;
            // Never knock out the column another builder is standing on.
            if(supportsOther(c,e.getKey()))continue;
            // Highest first so nobody is left standing on top of a pillar, then nearest;
            // the column this builder stands on comes first, dug down from the top.
            double score=-e.getKey().getY()*64+e.getKey().distToCenterSqr(c.player.position());
            BlockPos feet=c.player.blockPosition();
            if(feet.getX()==e.getKey().getX()&&feet.getZ()==e.getKey().getZ()&&feet.getY()>e.getKey().getY())score-=100000;
            if(score<bestScore){bestScore=score;best=e.getKey();}
        }
        if(best!=null){var s=list.get(best);list.put(best,new Scaffold(s.attempts,s.cooldown,c.player.getUUID(),now));}
        return best;
    }
    private LinkedHashMap<BlockPos,Scaffold> listOf(BlockPos p){return clearing.containsKey(p)?clearing:scaffolds;}
    /** Keeps a scaffold-removal or clearing claim alive. */
    public void touchScaffold(BlockPos p){var list=listOf(p);var s=list.get(p);if(s!=null)list.put(p,new Scaffold(s.attempts,s.cooldown,s.owner,level.getGameTime()));}
    /** The scaffold or clearing block is gone. */
    public void scaffoldRemoved(BlockPos p){
        if(clearing.remove(p)!=null){cleared++;progressed();}
        else if(scaffolds.remove(p)!=null)progressed();
    }
    public void scaffoldFailed(BlockPos p){
        var list=listOf(p);var s=list.get(p);if(s==null)return;
        if(s.attempts+1>=SCAFFOLD_ATTEMPTS){list.remove(p);if(list==clearing)kept++;else leftovers.add(p);return;}
        list.put(p,new Scaffold(s.attempts+1,level.getGameTime()+Math.min(400,40L<<s.attempts),null,0));
    }
    public void scaffoldLeftover(BlockPos p){if(scaffolds.remove(p)!=null)leftovers.add(p);}

    public String progress(){
        int total=schedule.size();
        if(stage==Stage.PLANNING)return "planning: "+(snapCursor<snapTotal?"surveying the site":"working out stands and scaffolding");
        if(stage==Stage.CLEARING)return "clearing: "+clearing.size()+" left"+(scanCursor<scanTotal?" (still surveying)":"")+", "+cleared+" removed"+(kept>0?", "+kept+" left in place":"");
        return schedule.done()+"/"+total+" placed"+(schedule.skipped()>0?", "+schedule.skipped()+" skipped":"")+(forced>0?", "+forced+" set directly":"")
            +", "+scaffolds.size()+" scaffold pending ("+scaffoldTotal+" used)";
    }
    public String status(){
        var text=new StringBuilder("Schematic ").append(plan.name()).append(" at ").append(plan.min().toShortString()).append(" → ").append(plan.max().toShortString())
            .append("\nStage: ").append(stage).append(stage==Stage.BUILDING?" ("+schedule.phase()+", layer "+(schedule.activeLayer()==null?"-":schedule.activeLayer()-plan.min().getY()+1)+")":"")
            .append("\nProgress: ").append(progress()).append("\nScaffold block: ").append(scaffoldId);
        if(planned!=null)text.append("\nPlan: ").append(planned.summary());
        if(!plan.skipped().isEmpty())text.append("\nNot buildable: ").append(plan.skipped());
        for(var b:builders){
            text.append("\n").append(b.player.getPlainTextName()).append(": ").append(b.tasks.status());
        }
        if(!leftovers.isEmpty())text.append("\nScaffold left in place: ").append(leftovers.size());
        if(!skips.isEmpty())text.append("\nRecently skipped:\n  ").append(String.join("\n  ",skips));
        return text.toString();
    }
}
