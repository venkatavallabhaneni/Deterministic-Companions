package dev.companions.crafting;

import java.util.*;
import java.util.function.BiPredicate;

/** Bounded assignment handles overlapping ingredients without spending a stack twice. */
public final class IngredientAllocator {
    public static int[] allocate(int ingredients,int[] counts,BiPredicate<Integer,Integer> accepts) {
        int[] chosen=new int[ingredients];Arrays.fill(chosen,-1);
        return assign(0,chosen,counts.clone(),accepts,new int[]{4096})?chosen:null;
    }
    private static boolean assign(int n,int[] chosen,int[] counts,BiPredicate<Integer,Integer> accepts,int[] budget) {
        if(n==chosen.length)return true;
        for(int slot=0;slot<counts.length;slot++) {
            if(--budget[0]<0)return false;
            if(counts[slot]==0||!accepts.test(n,slot))continue;
            counts[slot]--;chosen[n]=slot;
            if(assign(n+1,chosen,counts,accepts,budget))return true;
            counts[slot]++;chosen[n]=-1;
        }
        return false;
    }
}
