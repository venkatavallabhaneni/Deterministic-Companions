package dev.companions.navigation;

/** Retarget hysteresis for goals that move continuously, such as a protected player. */
public final class TrackingRetargetPolicy {
    private static final double ACTIVE_ROUTE_DRIFT = 6.0;
    private TrackingRetargetPolicy() {}

    public static boolean shouldRetarget(double driftSquared, boolean searching, boolean routeActive,
                                         boolean arrived, double tolerance) {
        // Let a bounded search finish against its immutable target. Restarting
        // for every player step can prevent a route from ever being produced.
        if (searching) return false;
        double threshold = routeActive && !arrived ? ACTIVE_ROUTE_DRIFT : Math.max(0.5, tolerance);
        return driftSquared > threshold * threshold;
    }
}
