package com.gamedead.game;

/** Shared immutable tile layout; coordinates and collision are independent of graphics. */
public final class ArenaMap {
    public static final int TILE = 16, COLUMNS = 40, ROWS = 24;
    public static final int WIDTH = TILE * COLUMNS, HEIGHT = TILE * ROWS;
    private ArenaMap() { }
    public static boolean wall(int column, int row) {
        return column < 1 || row < 1 || column >= COLUMNS - 1 || row >= ROWS - 1
                || (column >= 18 && column <= 21 && row >= 10 && row <= 13)
                || ((column == 10 || column == 29) && (row == 7 || row == 16));
    }
    public static boolean blocked(double x, double y, double radius) {
        for (int row = (int) Math.floor((y-radius)/TILE); row <= (int) Math.floor((y+radius)/TILE); row++) {
            for (int col = (int) Math.floor((x-radius)/TILE); col <= (int) Math.floor((x+radius)/TILE); col++) {
                if (!wall(col, row)) continue;
                double cx = Math.max(col*TILE, Math.min(x, (col+1)*TILE));
                double cy = Math.max(row*TILE, Math.min(y, (row+1)*TILE));
                if ((x-cx)*(x-cx)+(y-cy)*(y-cy) < radius*radius) return true;
            }
        }
        return false;
    }
}
