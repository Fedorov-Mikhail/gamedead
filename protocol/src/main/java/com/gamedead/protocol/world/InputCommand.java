package com.gamedead.protocol.world;
public record InputCommand(int protocolVersion, long sequence, double x, double y, boolean dash,
                           double aimX, double aimY, boolean attack, boolean revive) {
    public static final int VERSION = 2;
    public InputCommand(long sequence, double x, double y, boolean dash) {
        this(VERSION, sequence, x, y, dash, 0, 0, false, false);
    }
    public boolean valid() {
        return protocolVersion == VERSION && sequence >= 0 && axis(x) && axis(y) && axis(aimX) && axis(aimY);
    }
    private static boolean axis(double value) { return Double.isFinite(value) && Math.abs(value) <= 1; }
}
