package dev.companions;

import dev.companions.navigation.path.Cell;
import dev.companions.schematic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class SchematicBuildTest {
    @BeforeAll static void bootstrap(){VanillaCraftingDataTest.bootstrap();}

    // ---- tiny NBT writer for fixtures ------------------------------------------
    private static void named(DataOutputStream out,int type,String name) throws IOException {out.writeByte(type);out.writeUTF(name);}
    private static byte[] varints(int[] values){
        var out=new ByteArrayOutputStream();
        for(int v:values){while((v&~0x7f)!=0){out.write((v&0x7f)|0x80);v>>>=7;}out.write(v);}
        return out.toByteArray();
    }
    /** Sponge v2 (flat) or v3 (wrapped, "Blocks" container) file bytes. */
    static byte[] sponge(int version,int w,int h,int l,List<String> palette,int[] blocks) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))){
            named(out,10,version==3?"":"Schematic");
            if(version==3)named(out,10,"Schematic");
            named(out,3,"Version");out.writeInt(version);
            named(out,2,"Width");out.writeShort(w);named(out,2,"Height");out.writeShort(h);named(out,2,"Length");out.writeShort(l);
            if(version==3)named(out,10,"Blocks");
            named(out,10,"Palette");
            for(int i=0;i<palette.size();i++){named(out,3,palette.get(i));out.writeInt(i);}
            out.writeByte(0);
            byte[] data=varints(blocks);
            named(out,7,version==3?"Data":"BlockData");out.writeInt(data.length);out.write(data);
            if(version==3)out.writeByte(0);
            out.writeByte(0);
            if(version==3)out.writeByte(0);
        }
        return bytes.toByteArray();
    }
    static Schematic parse(byte[] file) throws IOException {return Schematic.parse("test",NbtReader.readCompressed(new ByteArrayInputStream(file)));}

    @Test void readsSpongeV2AndV3IncludingMultiByteVarints() throws IOException {
        var palette=new ArrayList<String>();palette.add("minecraft:air");
        for(int i=1;i<200;i++)palette.add("minecraft:filler_"+i);
        palette.set(1,"minecraft:stone");palette.set(150,"minecraft:oak_planks");
        int[] blocks={0,150,1,150,0,1,1,0};
        for(int version:new int[]{2,3}){
            var s=parse(sponge(version,2,2,2,palette,blocks));
            assertEquals(2,s.width());assertEquals(2,s.height());assertEquals(2,s.length());
            assertEquals("minecraft:oak_planks",s.state(1,0,0));
            assertEquals("minecraft:air",s.state(0,0,0));
            assertEquals("minecraft:stone",s.state(1,1,0));
            assertEquals(5,s.solidCount());
        }
    }
    @Test void rejectsTruncatedOrInconsistentData() throws IOException {
        var bad=sponge(2,2,1,1,List.of("minecraft:air","minecraft:stone"),new int[]{1});
        assertThrows(IOException.class,()->parse(bad));
        var badIndex=sponge(2,1,1,1,List.of("minecraft:air"),new int[]{5});
        assertThrows(IOException.class,()->parse(badIndex));
        assertThrows(IOException.class,()->NbtReader.readCompressed(new ByteArrayInputStream("not nbt".getBytes(StandardCharsets.UTF_8))));
    }

    // ---- shared schedule ----------------------------------------------------
    private static BuildSchedule tower(){
        // Two-high 1x1 column of solids plus a ladder at each height beside it.
        return new BuildSchedule(List.of(
            new BuildSchedule.Entry(new Cell(0,1,0),0),new BuildSchedule.Entry(new Cell(0,0,0),0),
            new BuildSchedule.Entry(new Cell(1,0,0),1),new BuildSchedule.Entry(new Cell(1,1,0),1),
            new BuildSchedule.Entry(new Cell(0,2,0),0)));
    }
    @Test void structureIsBuiltLowestLayerFirstAndFittingsLast(){
        var s=tower();UUID a=UUID.randomUUID();
        assertEquals(BuildSchedule.Phase.STRUCTURE,s.phase());
        // Layer 1 is only offered once the block beneath it is finished.
        assertEquals(List.of(1),s.offered(0));
        assertTrue(s.claim(1,a));assertTrue(s.offered(0).isEmpty());
        s.complete(1);
        assertEquals(List.of(0),s.offered(0),"lookahead allows the next layer above finished support");
        s.complete(0);
        assertEquals(List.of(4),s.offered(0));
        s.complete(4);
        assertEquals(BuildSchedule.Phase.FITTINGS,s.phase());
        assertEquals(List.of(2),s.offered(0),"ladders come only after every structural block, bottom up");
        s.complete(2);s.complete(3);
        assertEquals(BuildSchedule.Phase.DONE,s.phase());
    }
    @Test void furnishingsComeAfterFittingsWhichComeAfterStructure(){
        // A chest on the floor, a torch beside it, the floor under both.
        var s=new BuildSchedule(List.of(new BuildSchedule.Entry(new Cell(0,1,0),2),new BuildSchedule.Entry(new Cell(1,1,0),1),
            new BuildSchedule.Entry(new Cell(0,0,0),0),new BuildSchedule.Entry(new Cell(1,0,0),0)));
        assertEquals(BuildSchedule.Phase.STRUCTURE,s.phase());assertEquals(Set.of(2,3),new HashSet<>(s.offered(0)));
        s.complete(2);s.complete(3);
        assertEquals(BuildSchedule.Phase.FITTINGS,s.phase());assertEquals(List.of(1),s.offered(0),"the chest waits for the torch");
        s.complete(1);
        assertEquals(BuildSchedule.Phase.FURNISHINGS,s.phase());assertEquals(List.of(0),s.offered(0));
        s.complete(0);assertEquals(BuildSchedule.Phase.DONE,s.phase());
        s.reopen(3);assertEquals(BuildSchedule.Phase.STRUCTURE,s.phase(),"a lost floor block is rebuilt before anything else");
        assertThrows(IllegalArgumentException.class,()->new BuildSchedule.Entry(new Cell(0,0,0),3));
    }
    @Test void doorsAndGatesAreRoutedByOpeningThem(){
        var door=Blocks.OAK_DOOR.defaultBlockState();
        assertTrue(dev.companions.navigation.Passages.passage(door));
        assertTrue(dev.companions.navigation.Passages.openable(door));
        assertFalse(dev.companions.navigation.Passages.open(door));
        assertTrue(dev.companions.navigation.Passages.open(door.setValue(DoorBlock.OPEN,true)));
        assertFalse(dev.companions.navigation.Passages.openable(Blocks.IRON_DOOR.defaultBlockState()),"iron doors need redstone: a wall");
        assertTrue(dev.companions.navigation.Passages.openable(Blocks.OAK_FENCE_GATE.defaultBlockState()));
        assertFalse(dev.companions.navigation.Passages.passage(Blocks.OAK_TRAPDOOR.defaultBlockState()));
        // Why a door needs opening at all: its closed panel sits along one edge of the cell, clear of
        // the centred body box used for walkability, so the cell alone looks walkable.
        var panel=door.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).bounds();
        var body=new net.minecraft.world.phys.AABB(0.2,0.01,0.2,0.8,1.8,0.8);
        assertFalse(panel.intersects(body));
    }
    /** Walls around a 5 x 5 footprint at y0, roof at y1 covering x/z from -e to 4+e (e = eave overhang). */
    private static BuildSchedule roofed(int eave){
        var list=new ArrayList<BuildSchedule.Entry>();
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)if(x==0||x==4||z==0||z==4)list.add(new BuildSchedule.Entry(new Cell(x,0,z),0));
        for(int x=-eave;x<5+eave;x++)for(int z=-eave;z<5+eave;z++)list.add(new BuildSchedule.Entry(new Cell(x,1,z),0));
        return new BuildSchedule(list);
    }
    private static Set<Cell> offeredCells(BuildSchedule s){var out=new HashSet<Cell>();for(int i:s.offered(0))out.add(s.entry(i).pos());return out;}
    private static void finishOffered(BuildSchedule s){for(int i:s.offered(0))s.complete(i);}
    @Test void roofsAreBuiltRingByRingFromTheWallsInward(){
        var s=roofed(0);
        finishOffered(s);                                    // walls (and the roof ring resting on them, via lookahead)
        while(s.activeLayer()==0)finishOffered(s);
        assertEquals(1,s.activeLayer());
        var expected=new int[][]{{16},{8},{1}};
        for(int ring=0;ring<3;ring++){
            var cells=offeredCells(s);
            if(cells.isEmpty())break;
            for(var c:cells)assertEquals(ring,Math.max(Math.abs(c.x()-2),Math.abs(c.z()-2))==2?0:Math.max(Math.abs(c.x()-2),Math.abs(c.z()-2))==1?1:2,"cell "+c+" offered out of ring order");
            finishOffered(s);
        }
        assertEquals(BuildSchedule.Phase.DONE,s.phase());
    }
    @Test void outerRingRestsOnTheWallsAndEavesFollow(){
        var s=roofed(1);
        Map<Integer,Integer> rings=new TreeMap<>();
        for(int i=0;i<s.size();i++)if(s.entry(i).pos().y()==1)rings.merge(s.ring(i),1,Integer::sum);
        assertEquals(16,rings.get(0),"the roof ring directly over the walls goes first");
        assertEquals(8+20,rings.get(1),"then both the next inner ring and the eaves beside the walls");
        assertEquals(1+4,rings.get(2),"the centre and the eave corners, which only touch other eaves");
    }
    @Test void aLintelOverADoorwayWaitsForTheWallBesideIt(){
        // y0: two posts with a gap at x=1 (a doorway); y1: a full beam over it.
        var s=new BuildSchedule(List.of(new BuildSchedule.Entry(new Cell(0,0,0),0),new BuildSchedule.Entry(new Cell(2,0,0),0),
            new BuildSchedule.Entry(new Cell(0,1,0),0),new BuildSchedule.Entry(new Cell(1,1,0),0),new BuildSchedule.Entry(new Cell(2,1,0),0)));
        assertEquals(0,s.ring(2));assertEquals(1,s.ring(3));assertEquals(0,s.ring(4));
        finishOffered(s);finishOffered(s);
        assertEquals(Set.of(new Cell(1,1,0)),offeredCells(s));
    }
    @Test void aPillarIsPlannedInOneSearch(){
        var solid=new HashSet<Cell>();solid.add(new Cell(0,0,0));
        var t=new dev.companions.navigation.ConstructionMoves.Terrain(){
            public boolean clear(Cell p){return !solid.contains(p);}
            public boolean support(Cell p){return solid.contains(p);}
            public boolean replaceable(Cell p){return !solid.contains(p);}
            public boolean safe(Cell p){return true;}
        };
        Cell start=new Cell(0,1,0),goal=new Cell(0,7,0);
        var search=new dev.companions.navigation.path.BoundedAStar(start,goal,0,16,256,(cell,via)->dev.companions.navigation.ConstructionMoves.pillar(cell,64,t,
            via!=null&&via.action()==dev.companions.navigation.action.PathAction.PILLAR_UP&&cell.add(0,-1,0).equals(via.placement())));
        search.step(256);
        assertTrue(search.reached(),"six pillar steps chain inside one search");
        assertEquals(6,search.path().size());
        for(int k=0;k<6;k++)assertEquals(new Cell(0,1+k,0),search.path().get(k).placement());
        var single=new dev.companions.navigation.path.BoundedAStar(start,goal,0,16,256,(cell,via)->dev.companions.navigation.ConstructionMoves.pillar(cell,64,t));
        single.step(256);
        assertEquals(1,single.path().size(),"without the chained footing only one step could be planned");
    }
    @Test void clearingSurveysEveryCellOfTheAreaFromTheTopLayerDown(){
        var min=new BlockPos(-2,64,-2);var max=new BlockPos(8,70,6);
        long total=11L*7*9;
        var seen=new HashSet<BlockPos>();int lastY=Integer.MAX_VALUE;
        for(long i=0;i<total;i++){
            var p=SchematicJob.scanCell(min,max,i);
            assertTrue(p.getY()<=lastY,"layers are surveyed top-down");lastY=p.getY();
            assertTrue(p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ());
            assertTrue(seen.add(p),"cell visited twice: "+p);
        }
        assertEquals(total,seen.size());
        assertEquals(new BlockPos(-2,70,-2),SchematicJob.scanCell(min,max,0));
        assertEquals(new BlockPos(8,64,6),SchematicJob.scanCell(min,max,total-1));
    }
    @Test void claimsAreExclusiveAndReturnedWhenABuilderLeaves(){
        var s=tower();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertTrue(s.claim(1,a));assertFalse(s.claim(1,b));
        s.releaseAll(a,0);
        assertTrue(s.claim(1,b));assertEquals(b,s.owner(1));
    }
    @Test void repeatedFailuresBackOffThenSkipSoTheLayerBarrierNeverDeadlocks(){
        var s=tower();UUID a=UUID.randomUUID();long now=0;
        // Two full rounds: the layer gives its only block a second pass before moving up.
        for(int attempt=0;attempt<2*BuildSchedule.MAX_ATTEMPTS;attempt++){
            assertTrue(s.claim(1,a));s.release(1,now,true);
            if(attempt==BuildSchedule.MAX_ATTEMPTS-1)assertTrue(s.onSecondPass(1),"second pass starts once the layer has nothing else open");
            if(attempt<2*BuildSchedule.MAX_ATTEMPTS-1){assertEquals(List.of(1),s.offered(now),"an idle builder retries the only block instead of waiting");now+=10;}
        }
        assertEquals(BuildSchedule.State.SKIPPED,s.state(1));
        assertEquals(1,s.skipped());
        assertFalse(s.offered(now).isEmpty(),"the next layer opens after an impossible block is skipped");
        assertEquals(1,s.revive());
        assertEquals(BuildSchedule.State.PENDING,s.state(1));assertEquals(0,s.skipped());
    }
    @Test void aSkippedBlockGetsASecondPassBeforeItsLayerIsLeft(){
        // Layer 0: two blocks; layer 1: one block above the first.
        var s=new BuildSchedule(List.of(new BuildSchedule.Entry(new Cell(0,0,0),0),new BuildSchedule.Entry(new Cell(5,0,0),0),new BuildSchedule.Entry(new Cell(0,1,0),0)));
        UUID a=UUID.randomUUID();long now=0;
        for(int k=0;k<BuildSchedule.MAX_ATTEMPTS;k++){assertTrue(s.claim(1,a));s.release(1,now,true);now+=1000;}
        assertEquals(BuildSchedule.State.SKIPPED,s.state(1),"first round used up while the layer still has work");
        assertEquals(0,s.activeLayer());
        s.complete(0);
        assertEquals(BuildSchedule.State.PENDING,s.state(1),"the rest of the layer is done: one more round");
        assertTrue(s.onSecondPass(1));assertEquals(0,s.activeLayer(),"the layer is not left behind yet");
        s.complete(1);
        assertEquals(1,s.activeLayer());assertEquals(0,s.skipped());
    }
    @Test void onlyTheBuilderThatReachesALayerKeepsWorkingOnIt(){
        var r=new ReachTracker();UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        var all=List.of(a,b,c);
        r.reached(a,0,5);
        r.missed(b,0,5);
        assertFalse(r.benched(b,0,5,0,all),"one miss is not enough");
        r.missed(b,0,5);
        assertTrue(r.benched(b,0,5,0,all),"b keeps missing while a is placing blocks there");
        assertFalse(r.benched(c,0,5,0,all),"c has not tried yet");
        assertFalse(r.benched(a,0,5,0,all),"the builder that reaches is never benched");
        assertFalse(r.benched(b,0,6,0,all),"a bench is per layer");
        assertEquals(List.of(a),r.reaching(0,5,all));
        assertTrue(r.benched(b,0,5,ReachTracker.BENCH_TICKS-1,all));
        assertFalse(r.benched(b,0,5,ReachTracker.BENCH_TICKS,all),"after a while it gets another chance");
        assertFalse(r.benched(b,0,5,ReachTracker.BENCH_TICKS+1,all),"and its misses start from zero");
    }
    @Test void aBenchLiftsWhenNobodyElseReachesTheLayer(){
        var r=new ReachTracker();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        r.reached(a,0,3);r.missed(b,0,3);r.missed(b,0,3);
        assertTrue(r.benched(b,0,3,0,List.of(a,b)));
        assertFalse(r.benched(b,0,3,10,List.of(b)),"a left the job: b must try again");
        r.missed(b,0,3);r.missed(b,0,3);
        assertFalse(r.benched(b,0,3,20,List.of(b)),"with nobody reaching, nobody is benched");
        r.missed(b,0,3);r.reached(b,0,3);
        r.forget(a);
        assertFalse(r.benched(b,0,3,30,List.of(a,b)),"b reached it itself");
    }
    @Test void deferringForAnEntityInTheWayIsNotAFailure(){
        var s=tower();UUID a=UUID.randomUUID();
        for(int i=0;i<BuildSchedule.MAX_ATTEMPTS+2;i++){assertTrue(s.claim(1,a));s.defer(1,i*100L+50);}
        assertEquals(0,s.attempts(1));assertEquals(BuildSchedule.State.PENDING,s.state(1));
    }
    @Test void brokenFinishedBlocksReopenAndHoldBackLaterPhases(){
        var s=tower();
        for(int i=0;i<s.size();i++)s.complete(i);
        assertEquals(BuildSchedule.Phase.DONE,s.phase());
        s.reopen(1);
        assertEquals(BuildSchedule.Phase.STRUCTURE,s.phase());assertEquals(List.of(1),s.offered(0));
    }
    @Test void workSplitGivesEachBuilderAStrip(){
        assertEquals(0,WorkSplit.sector(5,0,10,1));
        var seen=new TreeSet<Integer>();
        for(int x=0;x<=15;x++)seen.add(WorkSplit.sector(x,0,15,4));
        assertEquals(Set.of(0,1,2,3),seen);
        assertEquals(0,WorkSplit.sector(-5,0,15,4));assertEquals(3,WorkSplit.sector(99,0,15,4));
    }

    // ---- planning and placement rules (vanilla 26.2 data) -----------------------
    @Test void planDefersFittingsMergesTwoPartBlocksAndCountsMaterials() throws IOException {
        var palette=List.of("minecraft:air","minecraft:cobblestone","minecraft:ladder[facing=north,waterlogged=false]",
            "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]","minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]",
            "minecraft:red_bed[facing=east,occupied=false,part=foot]","minecraft:red_bed[facing=east,occupied=false,part=head]",
            "minecraft:water[level=0]","minecraft:no_such_block","minecraft:oak_slab[type=double,waterlogged=false]");
        // 3 wide (x), 2 high, 1 long. index = (y*l+z)*w+x
        int[] blocks={1,3,5, 2,4,6};
        var plan=SchematicPlan.create(parse(sponge(2,3,2,1,palette,blocks)),new BlockPos(10,64,10),Rotation.NONE);
        var byPos=new HashMap<BlockPos,SchematicPlan.Placement>();for(var p:plan.placements())byPos.put(p.pos(),p);
        assertEquals(4,plan.placements().size(),"door top and bed head are created by their first part");
        assertEquals(PlacementRules.STRUCTURE,byPos.get(new BlockPos(10,64,10)).stage());
        assertTrue(byPos.get(new BlockPos(10,65,10)).fitting(),"ladder");
        var door=byPos.get(new BlockPos(11,64,10));
        assertTrue(door.fitting());assertEquals(new BlockPos(11,65,10),door.partner());
        var bed=byPos.get(new BlockPos(12,64,10));
        assertEquals(new BlockPos(13,64,10),bed.partner());assertEquals("minecraft:red_bed",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(bed.item()).toString());
        assertEquals(1,plan.materials().get(Items.OAK_DOOR));assertEquals(1,plan.materials().get(Items.LADDER));

        var odd=SchematicPlan.create(parse(sponge(2,3,1,1,palette,new int[]{7,8,9})),BlockPos.ZERO,Rotation.NONE);
        assertEquals(1,odd.placements().size());
        assertEquals(2,odd.materials().get(Items.OAK_SLAB),"a double slab needs two slabs");
        assertTrue(odd.skipped().keySet().stream().anyMatch(k->k.contains("water")));
        assertTrue(odd.skipped().keySet().stream().anyMatch(k->k.contains("no_such_block")));
    }
    @Test void rotationMovesPositionsAndRotatesFacing() throws IOException {
        var palette=List.of("minecraft:air","minecraft:ladder[facing=north,waterlogged=false]");
        // 2 wide, 1 high, 3 long; ladder at local (1,0,0)
        int[] blocks={0,1, 0,0, 0,0};
        var schem=parse(sponge(2,2,1,3,palette,blocks));
        var plan=SchematicPlan.create(schem,BlockPos.ZERO,Rotation.CLOCKWISE_90);
        assertEquals(new BlockPos(2,0,1),plan.max(),"footprint swaps to 3 x 2");
        var ladder=plan.placements().getFirst();
        assertEquals(new BlockPos(2,0,1),ladder.pos());
        assertEquals(Direction.EAST,ladder.state().getValue(LadderBlock.FACING));
        for(var r:Rotation.values()){
            var p=SchematicPlan.create(schem,BlockPos.ZERO,r).placements().getFirst().pos();
            assertTrue(p.getX()>=0&&p.getZ()>=0,"rotations keep the origin as the minimum corner: "+r);
        }
    }
    @Test void stateMatchingIgnoresNeighbourDerivedProperties(){
        var fence=Blocks.OAK_FENCE.defaultBlockState();
        assertTrue(PlacementRules.matches(fence.setValue(FenceBlock.NORTH,true),fence));
        var door=Blocks.OAK_DOOR.defaultBlockState();
        assertTrue(PlacementRules.matches(door.setValue(DoorBlock.OPEN,true),door),"navigation may leave a door open");
        assertFalse(PlacementRules.matches(door.setValue(DoorBlock.FACING,Direction.EAST),door.setValue(DoorBlock.FACING,Direction.WEST)));
        var top=Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP);
        assertFalse(PlacementRules.matches(Blocks.OAK_SLAB.defaultBlockState(),top));
        assertEquals(top,PlacementRules.adjust(Blocks.OAK_SLAB.defaultBlockState(),top));
        assertFalse(PlacementRules.matches(Blocks.TORCH.defaultBlockState(),Blocks.WALL_TORCH.defaultBlockState()));
    }
    @Test void chestPairingIsPartOfWhatWasBuilt(){
        var single=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.WEST).setValue(ChestBlock.TYPE,ChestType.SINGLE);
        var joined=single.setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.LEFT);
        // A chest accidentally joined to its neighbour (and facing the neighbour's way) is not finished...
        assertFalse(PlacementRules.matches(joined,single));
        assertFalse(PlacementRules.matches(single.setValue(ChestBlock.TYPE,ChestType.RIGHT),single),"facing alone is not enough");
        // ...and realigning restores both facing and pairing.
        assertEquals(single,PlacementRules.adjust(joined,single).setValue(ChestBlock.WATERLOGGED,false));
        assertTrue(PlacementRules.matches(single.setValue(ChestBlock.WATERLOGGED,true),single),"waterlogging is not compared");
    }
    @Test void attachmentsAreClickedAgainstTheirSupport(){
        var ladder=Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH);
        assertEquals(Direction.SOUTH,PlacementRules.supportOrder(ladder).getFirst());
        assertNull(PlacementRules.placerFacing(ladder));
        assertEquals(Direction.DOWN,PlacementRules.supportOrder(Blocks.TORCH.defaultBlockState()).getFirst());
        assertEquals(Direction.UP,PlacementRules.supportOrder(Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true)).getFirst());
        assertEquals(Direction.UP,PlacementRules.supportOrder(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF,Half.TOP)).getFirst());
        var chest=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.WEST);
        assertEquals(Direction.EAST,PlacementRules.placerFacing(chest),"chests face back toward the player");
        var stairs=Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.WEST);
        assertEquals(Direction.WEST,PlacementRules.placerFacing(stairs));
        assertEquals(6,new HashSet<>(PlacementRules.supportOrder(Blocks.STONE.defaultBlockState())).size());
    }
    private static Block block(String id){return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(id));}
    @Test void structuralAndFittingClassification(){
        for(var b:List.of(Blocks.LADDER,Blocks.TORCH,Blocks.WALL_TORCH,Blocks.OAK_DOOR,block("red_bed"),Blocks.LANTERN,Blocks.OAK_BUTTON,Blocks.LEVER,Blocks.VINE,Blocks.RAIL,block("white_carpet"),Blocks.OAK_SIGN,Blocks.POPPY))
            assertEquals(PlacementRules.FITTINGS,PlacementRules.stage(b.defaultBlockState()),b.toString());
        for(var b:List.of(Blocks.COBBLESTONE,Blocks.OAK_PLANKS,Blocks.GLASS,Blocks.OAK_STAIRS,Blocks.OAK_SLAB,Blocks.OAK_FENCE,Blocks.GLASS_PANE,Blocks.BOOKSHELF))
            assertEquals(PlacementRules.STRUCTURE,PlacementRules.stage(b.defaultBlockState()),b.toString());
        for(var b:List.of(Blocks.CHEST,Blocks.TRAPPED_CHEST,Blocks.BARREL,Blocks.CRAFTING_TABLE,Blocks.FURNACE,Blocks.SMOKER,Blocks.BLAST_FURNACE,Blocks.ANVIL,Blocks.GRINDSTONE,Blocks.ENCHANTING_TABLE,Blocks.SMITHING_TABLE,Blocks.LECTERN))
            assertEquals(PlacementRules.FURNISHINGS,PlacementRules.stage(b.defaultBlockState()),b.toString());
        assertNotNull(PlacementRules.unsupported(Blocks.WATER.defaultBlockState()));
        assertNull(PlacementRules.unsupported(Blocks.WALL_TORCH.defaultBlockState()),"wall torches are placed with a torch item");
        assertSame(Items.TORCH,PlacementRules.item(Blocks.WALL_TORCH.defaultBlockState()));
    }
    @Test void defaultOriginIsInFrontOfThePlayerAndNeverUnderThem(){
        BlockPos feet=new BlockPos(0,64,0);
        for(Direction d:Direction.Plane.HORIZONTAL){
            var origin=dev.companions.command.SchematicCommands.defaultOrigin(feet,d,5,7);
            boolean insideX=feet.getX()>=origin.getX()&&feet.getX()<=origin.getX()+4,insideZ=feet.getZ()>=origin.getZ()&&feet.getZ()<=origin.getZ()+6;
            assertFalse(insideX&&insideZ,"facing "+d);
            assertEquals(64,origin.getY());
        }
    }
}
