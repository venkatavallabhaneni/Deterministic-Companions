package dev.companions.selection;

import dev.companions.combat.GuardArea;
import java.util.*;

/** Per-operator selection; no world references or shared corners between players. */
public final class GuardSelection {
    public record Corner(int x,int y,int z){}
    public final List<String> names;
    public final String dimension;
    private Corner first,second;
    private boolean applied;
    public GuardSelection(List<String> names,String dimension){this.names=List.copyOf(names);this.dimension=dimension;}
    public static List<String> parseNames(String input){
        if(input.equalsIgnoreCase("all"))return List.of("all");
        String[] parts=input.split(",",-1);var names=new LinkedHashSet<String>();
        for(String part:parts){String name=part.trim().toLowerCase(Locale.ROOT);if(!name.matches("[a-z0-9_]{1,16}")||name.equals("all"))throw new IllegalArgumentException("Use all or comma-separated bot names, for example bob,tracy");if(!names.add(name))throw new IllegalArgumentException("Duplicate companion: "+name);}
        if(names.size()>dev.companions.config.CompanionConfig.MAX_ACTIVE)throw new IllegalArgumentException("Maximum guards: 4");
        return List.copyOf(names);
    }
    public void select(boolean primary,Corner corner,String dimension){
        if(!this.dimension.equals(dimension))throw new IllegalArgumentException("Both corners must be in the dimension where you requested the wand");
        if(primary){first=corner;second=null;applied=false;}else{
            if(first==null)throw new IllegalArgumentException("Left-click the first corner before right-clicking the second");
            between(first,corner);if(!corner.equals(second))applied=false;second=corner;
        }
    }
    private static GuardArea between(Corner a,Corner b){return GuardArea.between(a.x,a.y,a.z,b.x,b.y,b.z);}
    public boolean complete(){return first!=null&&second!=null;}
    public boolean applied(){return applied;}
    public void markApplied(){if(!complete())throw new IllegalStateException("Select both corners first");applied=true;}
    public GuardArea preview(){return first==null?null:between(first,second==null?first:second);}
}
