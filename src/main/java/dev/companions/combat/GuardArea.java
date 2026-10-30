package dev.companions.combat;

/** Inclusive block coordinates; reversed corners and one-block-wide areas are valid. */
public record GuardArea(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
    public GuardArea {
        if(minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("Guard area corners must be normalized");
        if((long)maxX-minX>=128||(long)maxY-minY>=128||(long)maxZ-minZ>=128)throw new IllegalArgumentException("Guard areas may span at most 128 blocks on each axis");
    }
    public static GuardArea between(int x,int y,int z,int xx,int yy,int zz){return new GuardArea(Math.min(x,xx),Math.min(y,yy),Math.min(z,zz),Math.max(x,xx),Math.max(y,yy),Math.max(z,zz));}
    public boolean contains(double x,double y,double z){return x>=minX&&x<(double)maxX+1&&y>=minY&&y<(double)maxY+1&&z>=minZ&&z<(double)maxZ+1;}
    public String describe(){return minX+" "+minY+" "+minZ+" to "+maxX+" "+maxY+" "+maxZ;}
}
