package dev.companions.navigation.path;

import java.util.Deque;

/** Keeps executable actions intact when repairing a moving target's route tail. */
public final class RouteReuse {
    private RouteReuse() {}
    public static void keepPrefix(Deque<PathEdge> route,int length) {
        if(length<1)throw new IllegalArgumentException("A repair needs an anchor edge");
        while(route.size()>length)route.removeLast();
    }
}
