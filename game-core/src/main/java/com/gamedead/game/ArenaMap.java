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

    static double firstWallContact(double x, double y, double dx, double dy, double radius) {
        double first = Double.POSITIVE_INFINITY;
        int left = (int) Math.floor((Math.min(x, x + dx) - radius) / TILE);
        int right = (int) Math.floor((Math.max(x, x + dx) + radius) / TILE);
        int bottom = (int) Math.floor((Math.min(y, y + dy) - radius) / TILE);
        int top = (int) Math.floor((Math.max(y, y + dy) + radius) / TILE);
        for (int row = bottom; row <= top; row++) {
            for (int col = left; col <= right; col++) {
                if (!wall(col, row)) continue;
                double x0 = col * TILE, x1 = x0 + TILE, y0 = row * TILE, y1 = y0 + TILE;
                // A circle swept against a tile is two edge strips and four rounded corners.
                first = Math.min(first, boxContact(x, y, dx, dy, x0 - radius, x1 + radius, y0, y1));
                first = Math.min(first, boxContact(x, y, dx, dy, x0, x1, y0 - radius, y1 + radius));
                first = Math.min(first, circleContact(x, y, dx, dy, x0, y0, radius));
                first = Math.min(first, circleContact(x, y, dx, dy, x1, y0, radius));
                first = Math.min(first, circleContact(x, y, dx, dy, x0, y1, radius));
                first = Math.min(first, circleContact(x, y, dx, dy, x1, y1, radius));
            }
        }
        return first;
    }

    static double circleContact(double x, double y, double dx, double dy, double cx, double cy, double radius) {
        double fx = x - cx, fy = y - cy;
        double c = fx * fx + fy * fy - radius * radius;
        if (c <= 0) return 0;
        double a = dx * dx + dy * dy;
        double b = 2 * (fx * dx + fy * dy);
        double discriminant = b * b - 4 * a * c;
        if (a == 0 || discriminant < 0) return Double.POSITIVE_INFINITY;
        double t = (-b - Math.sqrt(discriminant)) / (2 * a);
        return t >= 0 && t <= 1 ? t : Double.POSITIVE_INFINITY;
    }

    private static double boxContact(double x, double y, double dx, double dy,
                                     double left, double right, double bottom, double top) {
        double entry = 0, exit = 1;
        for (int axis = 0; axis < 2; axis++) {
            double origin = axis == 0 ? x : y, delta = axis == 0 ? dx : dy;
            double low = axis == 0 ? left : bottom, high = axis == 0 ? right : top;
            if (delta == 0) {
                if (origin < low || origin > high) return Double.POSITIVE_INFINITY;
            } else {
                double one = (low - origin) / delta, two = (high - origin) / delta;
                entry = Math.max(entry, Math.min(one, two));
                exit = Math.min(exit, Math.max(one, two));
                if (entry > exit) return Double.POSITIVE_INFINITY;
            }
        }
        return entry;
    }
}
