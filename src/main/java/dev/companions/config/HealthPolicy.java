package dev.companions.config;

public final class HealthPolicy {
    public static final float DEFAULT=20,MAX=1024;
    public static float validate(float value){if(!Float.isFinite(value)||value<1||value>MAX)throw new IllegalArgumentException("Health must be 1–1024 points (2 points = 1 heart)");return value;}
    public static float restore(float value){return !Float.isFinite(value)||value<1?DEFAULT:Math.min(MAX,value);}
}
