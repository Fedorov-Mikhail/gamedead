package com.gamedead.game;

import java.util.*;

/** Mutable match state; only the server's fixed-step loop may mutate a world. */
public final class ArenaWorld {
    public static final int TICKS_PER_SECOND = 20;
    public static final int MAX_ENEMIES = 40, MAX_PROJECTILES = 128;
    public enum Phase { RUNNING, WON, LOST, ABORTED }
    public record Player(UUID id, double x, double y, double dashCooldown, boolean moving,
                         int health, double reviveProgress) { }
    public record Enemy(long id, double x, double y, int health) { }
    public record Projectile(long id, double x, double y) { }

    private static final double RADIUS = 5, SPEED_PER_TICK = 4, DASH_DISTANCE = 80;
    private static final int INPUT_LIFETIME = 6, DASH_COOLDOWN = 40, REVIVE_TICKS = 60;
    private final Map<UUID, Body> bodies = new LinkedHashMap<>();
    private final List<EnemyBody> enemies = new ArrayList<>();
    private final List<Bolt> bolts = new ArrayList<>();
    private final Random random = new Random(0);
    private final int durationTicks;
    private long tick, nextEntityId;
    private int kills;
    private Phase phase = Phase.RUNNING;

    public ArenaWorld(List<UUID> players) { this(players, 240); }

    public ArenaWorld(List<UUID> players, int durationSeconds) {
        if (players.isEmpty() || players.size() > 4 || players.stream().anyMatch(Objects::isNull)
                || new HashSet<>(players).size() != players.size()) {
            throw new IllegalArgumentException("World requires 1–4 distinct players");
        }
        if (durationSeconds < 1 || durationSeconds > 1800) {
            throw new IllegalArgumentException("Match duration must be 1–1800 seconds");
        }
        durationTicks = durationSeconds * TICKS_PER_SECOND;
        double[][] spawns = {{80, 192}, {560, 192}, {320, 80}, {320, 304}};
        for (int i = 0; i < players.size(); i++) {
            bodies.put(players.get(i), new Body(players.get(i), spawns[i][0], spawns[i][1]));
        }
    }

    public void input(UUID player, long sequence, double x, double y, boolean dash) {
        input(player, sequence, x, y, dash, 0, 0, false, false);
    }

    public void input(UUID player, long sequence, double x, double y, boolean dash,
                      double aimX, double aimY, boolean attack, boolean revive) {
        if (!axis(x) || !axis(y) || !axis(aimX) || !axis(aimY) || sequence < 0) {
            throw new IllegalArgumentException("Invalid combat input");
        }
        Body body = bodies.get(player);
        if (body == null || sequence <= body.sequence || phase != Phase.RUNNING) return;
        body.sequence = sequence;
        double length = Math.max(1, Math.hypot(x, y));
        body.dx = x / length;
        body.dy = y / length;
        double aimLength = Math.hypot(aimX, aimY);
        body.aimX = aimLength > 0 ? aimX / aimLength : 0;
        body.aimY = aimLength > 0 ? aimY / aimLength : 0;
        body.attack = attack;
        body.revive = revive;
        body.expires = tick + INPUT_LIFETIME;
        body.dashRequested |= dash;
    }

    private static boolean axis(double value) { return Double.isFinite(value) && Math.abs(value) <= 1; }

    public void clearInput(UUID player) {
        Body body = bodies.get(player);
        if (body == null) return;
        body.dx = body.dy = 0;
        body.dashRequested = body.moving = body.attack = body.revive = false;
    }

    public void resetConnection(UUID player) {
        clearInput(player);
        Body body = bodies.get(player);
        if (body != null) body.sequence = -1;
    }

    public void remove(UUID player) {
        bodies.remove(player);
        checkDefeat();
    }

    public void abort() { if (phase == Phase.RUNNING) phase = Phase.ABORTED; }

    public void tick() {
        if (phase != Phase.RUNNING) return;
        tick++;
        for (Body body : bodies.values()) {
            if (body.cooldown > 0) body.cooldown--;
            if (body.protection > 0) body.protection--;
            if (body.fireCooldown > 0) body.fireCooldown--;
            if (tick > body.expires || body.health == 0) clearInput(body.id);
            if (body.health == 0) continue;
            double previousX = body.x, previousY = body.y;
            if (body.dashRequested && body.cooldown == 0 && Math.hypot(body.dx, body.dy) > 0) {
                move(body, body.dx * DASH_DISTANCE, body.dy * DASH_DISTANCE);
                body.cooldown = DASH_COOLDOWN;
            }
            body.dashRequested = false;
            move(body, body.dx * SPEED_PER_TICK, body.dy * SPEED_PER_TICK);
            body.moving = body.x != previousX || body.y != previousY;
            if (body.attack && !body.revive && body.fireCooldown == 0
                    && Math.hypot(body.aimX, body.aimY) > 0 && bolts.size() < MAX_PROJECTILES) {
                bolts.add(new Bolt(++nextEntityId, body.x, body.y, body.aimX * 16, body.aimY * 16));
                body.fireCooldown = 7;
            }
            if (body.distances == null || tick % 10 == 0) body.distances = distances(body.x, body.y);
        }
        advanceBolts();
        if (tick >= 200 && tick % 40 == 0) {
            for (int i = 0; i < bodies.size(); i++) spawnAwayFromPlayers();
        }
        advanceEnemies();
        reviveAllies();
        bodies.values().forEach(b -> b.damaged = false);
        checkDefeat();
        if (phase == Phase.RUNNING && tick >= durationTicks) phase = Phase.WON;
    }

    private void move(Body body, double dx, double dy) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 2));
        for (int i = 0; i < steps; i++) {
            double x = body.x + dx / steps, y = body.y + dy / steps;
            if (!ArenaMap.blocked(x, body.y, RADIUS)) body.x = x;
            if (!ArenaMap.blocked(body.x, y, RADIUS)) body.y = y;
        }
    }

    private void advanceBolts() {
        for (Iterator<Bolt> iterator = bolts.iterator(); iterator.hasNext();) {
            Bolt bolt = iterator.next();
            double first = ArenaMap.firstWallContact(bolt.x, bolt.y, bolt.dx, bolt.dy, 2);
            EnemyBody target = null;
            for (EnemyBody enemy : enemies) {
                if (enemy.health <= 0) continue;
                double contact = ArenaMap.circleContact(bolt.x, bolt.y, bolt.dx, bolt.dy, enemy.x, enemy.y, 7);
                if (contact < first) { first = contact; target = enemy; }
            }
            boolean hit = Double.isFinite(first);
            bolt.x += bolt.dx * (hit ? first : 1);
            bolt.y += bolt.dy * (hit ? first : 1);
            if (target != null) {
                target.health -= 20;
                if (target.health <= 0) kills++;
            }
            if (hit || --bolt.lifetime == 0) iterator.remove();
        }
        enemies.removeIf(enemy -> enemy.health <= 0);
    }

    void spawnEnemy(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0
                || x > ArenaMap.WIDTH || y > ArenaMap.HEIGHT || ArenaMap.blocked(x, y, RADIUS)) {
            throw new IllegalArgumentException("Enemy must spawn on walkable ground");
        }
        if (phase == Phase.RUNNING && enemies.size() < MAX_ENEMIES) {
            enemies.add(new EnemyBody(++nextEntityId, x, y));
        }
    }

    private void spawnAwayFromPlayers() {
        if (enemies.size() >= MAX_ENEMIES) return;
        for (int attempt = 0; attempt < 80; attempt++) {
            double x = (1 + random.nextInt(ArenaMap.COLUMNS - 2)) * 16 + 8;
            double y = (1 + random.nextInt(ArenaMap.ROWS - 2)) * 16 + 8;
            if (ArenaMap.blocked(x, y, RADIUS)) continue;
            if (bodies.values().stream().anyMatch(b -> Math.hypot(x - b.x, y - b.y) < 128)) continue;
            spawnEnemy(x, y);
            return;
        }
    }

    private void advanceEnemies() {
        for (EnemyBody enemy : enemies) {
            int cell = cell(enemy.x, enemy.y);
            Body target = bodies.values().stream().filter(b -> b.health > 0 && b.distances != null)
                    .min(Comparator.comparingInt(b -> b.distances[cell])).orElse(null);
            if (target == null || target.distances[cell] == Integer.MAX_VALUE) continue;
            if (enemy.waypoint < 0 || Math.hypot(enemy.x - centerX(enemy.waypoint), enemy.y - centerY(enemy.waypoint)) < 1.6) {
                enemy.waypoint = cell;
                for (int next : neighbors(cell)) {
                    if (target.distances[next] < target.distances[enemy.waypoint]) enemy.waypoint = next;
                }
            }
            boolean sameCell = cell == cell(target.x, target.y);
            double dx = (sameCell ? target.x : centerX(enemy.waypoint)) - enemy.x;
            double dy = (sameCell ? target.y : centerY(enemy.waypoint)) - enemy.y;
            double scale = 1.5 / Math.max(1.5, Math.hypot(dx, dy));
            if (!ArenaMap.blocked(enemy.x + dx * scale, enemy.y, RADIUS)) enemy.x += dx * scale;
            if (!ArenaMap.blocked(enemy.x, enemy.y + dy * scale, RADIUS)) enemy.y += dy * scale;
            for (Body body : bodies.values()) {
                if (body.health > 0 && Math.hypot(enemy.x - body.x, enemy.y - body.y) <= 10) damage(body.id, 10);
            }
        }
    }

    private static int cell(double x, double y) { return (int) (y / 16) * ArenaMap.COLUMNS + (int) (x / 16); }
    private static double centerX(int cell) { return (cell % ArenaMap.COLUMNS) * 16 + 8; }
    private static double centerY(int cell) { return (cell / ArenaMap.COLUMNS) * 16 + 8; }
    private static int[] neighbors(int cell) {
        int col = cell % ArenaMap.COLUMNS, row = cell / ArenaMap.COLUMNS;
        return new int[]{row * ArenaMap.COLUMNS + Math.max(0, col - 1),
                row * ArenaMap.COLUMNS + Math.min(ArenaMap.COLUMNS - 1, col + 1),
                Math.max(0, row - 1) * ArenaMap.COLUMNS + col,
                Math.min(ArenaMap.ROWS - 1, row + 1) * ArenaMap.COLUMNS + col};
    }

    private static int[] distances(double x, double y) {
        int[] distance = new int[ArenaMap.COLUMNS * ArenaMap.ROWS];
        Arrays.fill(distance, Integer.MAX_VALUE);
        int start = cell(x, y);
        distance[start] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next : neighbors(current)) {
                if (distance[next] != Integer.MAX_VALUE || ArenaMap.blocked(centerX(next), centerY(next), RADIUS)) continue;
                distance[next] = distance[current] + 1;
                queue.addLast(next);
            }
        }
        return distance;
    }

    void damage(UUID id, int amount) {
        Body body = bodies.get(id);
        if (phase != Phase.RUNNING || body == null || body.health == 0 || body.protection > 0 || amount <= 0) return;
        body.health = Math.max(0, body.health - amount);
        body.protection = 10;
        body.damaged = true;
        if (body.health == 0) clearInput(id);
    }

    private void reviveAllies() {
        Set<UUID> rescuers = new HashSet<>();
        for (Body down : bodies.values()) {
            if (down.health > 0) continue;
            Body helper = bodies.values().stream().filter(b -> b.health > 0 && b.revive && !b.damaged && !rescuers.contains(b.id)
                    && Math.hypot(b.x - down.x, b.y - down.y) <= 24 && clearLine(b, down)).findFirst().orElse(null);
            if (helper == null) { down.rescuer = null; down.reviveTicks = 0; continue; }
            rescuers.add(helper.id);
            if (!helper.id.equals(down.rescuer)) { down.rescuer = helper.id; down.reviveTicks = 0; }
            if (++down.reviveTicks >= REVIVE_TICKS) {
                down.health = 40;
                down.protection = 20;
                down.reviveTicks = 0;
                down.rescuer = null;
            }
        }
    }

    private static boolean clearLine(Body one, Body two) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(one.x - two.x, one.y - two.y) / 2));
        for (int i = 1; i < steps; i++) {
            if (ArenaMap.blocked(one.x + (two.x - one.x) * i / steps, one.y + (two.y - one.y) * i / steps, 0.5)) return false;
        }
        return true;
    }

    private void checkDefeat() {
        if (phase == Phase.RUNNING && bodies.values().stream().noneMatch(b -> b.health > 0)) phase = Phase.LOST;
    }

    public long tickNumber() { return tick; }
    public Phase phase() { return phase; }
    public int remainingTicks() { return (int) Math.max(0, durationTicks - tick); }
    public int kills() { return kills; }
    public List<Player> players() {
        return bodies.values().stream().map(b -> new Player(b.id, b.x, b.y, b.cooldown / 20.0, b.moving,
                b.health, b.reviveTicks / (double) REVIVE_TICKS)).toList();
    }
    public List<Enemy> enemies() {
        return enemies.stream().map(e -> new Enemy(e.id, e.x, e.y, e.health)).toList();
    }
    public List<Projectile> projectiles() {
        return bolts.stream().map(b -> new Projectile(b.id, b.x, b.y)).toList();
    }

    private static final class Body {
        final UUID id;
        double x, y, dx, dy, aimX, aimY;
        int cooldown, fireCooldown, protection, reviveTicks, health = 100;
        long sequence = -1, expires;
        boolean dashRequested, moving, attack, revive, damaged;
        UUID rescuer;
        int[] distances;
        Body(UUID id, double x, double y) { this.id = id; this.x = x; this.y = y; }
    }
    private static final class EnemyBody {
        final long id;
        double x, y;
        int health = 60, waypoint = -1;
        EnemyBody(long id, double x, double y) { this.id = id; this.x = x; this.y = y; }
    }
    private static final class Bolt {
        final long id;
        final double dx, dy;
        double x, y;
        int lifetime = 40;
        Bolt(long id, double x, double y, double dx, double dy) {
            this.id = id; this.x = x; this.y = y; this.dx = dx; this.dy = dy;
        }
    }
}
