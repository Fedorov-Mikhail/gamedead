package com.gamedead.game;

import java.util.*;

/** Mutable match state; the server serializes all mutations through its room registry. */
public final class ArenaWorld {
    public static final int TICKS_PER_SECOND = 20;
    public static final int MAX_ENEMIES = 40, MAX_PROJECTILES = 128;
    public enum Phase { RUNNING, WON, LOST, ABORTED }
    public record Player(UUID id, double x, double y, double dashCooldown, boolean moving,
                         int health, double reviveProgress,int maxHealth,int pendingUpgrades,long choiceId,
                         List<Upgrade> upgradeOffers,Map<Upgrade,Integer> upgrades) { }
    public record ResultPlayer(UUID id,int kills,int damage,int level,boolean left,Map<Upgrade,Integer> upgrades) { }
    void gainExperience(int amount) {
        if(amount<0 || amount>100_000)throw new IllegalArgumentException("Invalid experience");
        if(phase!=Phase.RUNNING)return;
        totalExperience=Math.min(1_000_000,totalExperience+amount);
        while(level<10 && totalExperience>=nextLevelThreshold){
            levelStartExperience=nextLevelThreshold;level++;
            nextLevelThreshold+=roster.size()*(30+(level-1)*15);
            for(Body body:bodies.values()){
                body.pendingUpgrades++;body.levelReached=level;
                if(body.offers.isEmpty())body.offers=offers(body);
            }
        }
    }
    public int level(){return level;}
    public int experience(){return level==10?0:totalExperience-levelStartExperience;}
    public int requiredExperience(){return level==10?0:nextLevelThreshold-levelStartExperience;}
    public int totalExperience(){return totalExperience;}
    public boolean chooseUpgrade(UUID player,long choiceId,String upgrade){
        Body body=bodies.get(player);
        if(phase!=Phase.RUNNING || body==null || body.pendingUpgrades==0 || choiceId!=body.choicesMade+1)return false;
        Upgrade selected;
        try{selected=Upgrade.valueOf(upgrade);}catch(IllegalArgumentException | NullPointerException ignored){return false;}
        if(!body.offers.contains(selected))return false;
        int previousMax=body.loadout.maxHealth();
        if(!body.loadout.add(selected))return false;
        if(body.health>0)body.health+=body.loadout.maxHealth()-previousMax;
        body.pendingUpgrades--;body.choicesMade++;
        body.offers=body.pendingUpgrades>0?offers(body):List.of();
        return true;
    }
    private List<Upgrade> offers(Body body){
        List<Upgrade> available=new ArrayList<>();
        for(Upgrade upgrade:Upgrade.values())if(body.loadout.rank(upgrade)<upgrade.maxRank())available.add(upgrade);
        Collections.shuffle(available,random);
        return List.copyOf(available.subList(0,Math.min(3,available.size())));
    }
    public List<ResultPlayer> results(){
        return roster.values().stream().map(b->new ResultPlayer(b.id,b.kills,b.damage,b.levelReached,!bodies.containsKey(b.id),b.loadout.snapshot())).toList();
    }
    public record Enemy(long id,double x,double y,int health,int maxHealth,EnemyKind kind,
                        String action,int actionTicks,double aimX,double aimY,double radius) { }
    public record Projectile(long id,double x,double y,double velocityX,double velocityY,boolean hostile) { }

    private static final double RADIUS = 5, DASH_DISTANCE = 80;
    private static final int INPUT_LIFETIME = 6, REVIVE_TICKS = 60;
    private final Map<UUID, Body> bodies = new LinkedHashMap<>();
    private final Map<UUID, Body> roster = new LinkedHashMap<>();
    private final List<EnemyBody> enemies = new ArrayList<>();
    private final List<Bolt> bolts = new ArrayList<>();
    private final Random random = new Random(0);
    private final int durationTicks;
    private final Difficulty difficulty;
    private long tick, nextEntityId;
    private long nextSpawnTick=200;
    private boolean bossSpawned,bossDefeated;
    private int kills;
    private int level=1,totalExperience,levelStartExperience,nextLevelThreshold;
    private Phase phase = Phase.RUNNING;

    public ArenaWorld(List<UUID> players) { this(players, 1800); }

    public ArenaWorld(List<UUID> players,int durationSeconds) { this(players,durationSeconds,Difficulty.NORMAL); }

    public ArenaWorld(List<UUID> players, int durationSeconds,Difficulty difficulty) {
        if (players.isEmpty() || players.size() > 4 || players.stream().anyMatch(Objects::isNull)
                || new HashSet<>(players).size() != players.size()) {
            throw new IllegalArgumentException("World requires 1–4 distinct players");
        }
        if (durationSeconds < 1 || durationSeconds > 1800) {
            throw new IllegalArgumentException("Match duration must be 1–1800 seconds");
        }
        durationTicks = durationSeconds * TICKS_PER_SECOND;
        this.difficulty=Objects.requireNonNull(difficulty,"difficulty");
        nextLevelThreshold=players.size()*30;
        double[][] spawns = {{80, 192}, {560, 192}, {320, 80}, {320, 304}};
        for (int i = 0; i < players.size(); i++) {
            Body body=new Body(players.get(i), spawns[i][0], spawns[i][1]);
            bodies.put(body.id,body);roster.put(body.id,body);
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
                body.cooldown = body.loadout.dashTicks();
            }
            body.dashRequested = false;
            move(body, body.dx * body.loadout.speed(), body.dy * body.loadout.speed());
            body.moving = body.x != previousX || body.y != previousY;
            if (body.attack && !body.revive && body.fireCooldown == 0
                    && Math.hypot(body.aimX, body.aimY) > 0 && bolts.size() < MAX_PROJECTILES) {
                int count=body.loadout.projectiles();
                for(int i=0;i<count && bolts.size()<MAX_PROJECTILES;i++){
                    double angle=(i-(count-1)/2.0)*0.16,cos=Math.cos(angle),sin=Math.sin(angle);
                    bolts.add(new Bolt(++nextEntityId,body.id,body.loadout.damage(),body.x,body.y,
                            (body.aimX*cos-body.aimY*sin)*16,(body.aimX*sin+body.aimY*cos)*16));
                }
                body.fireCooldown = body.loadout.fireTicks();
            }
            if (body.distances == null || tick % 10 == 0) {
                body.distances = distances(body.x, body.y,RADIUS);
                body.bossDistances = distances(body.x,body.y,10);
            }
        }
        advanceBolts();
        if (!bossSpawned && tick >= (durationTicks*9+9)/10) spawnBoss();
        if (!bossSpawned && tick >= nextSpawnTick) {
            for (int i = 0; i < roster.size(); i++) spawnAwayFromPlayers(nextEnemyKind());
            nextSpawnTick=tick+Math.max(10,Math.round((40-wave())/difficulty.spawnMultiplier()));
        }
        advanceEnemies();
        reviveAllies();
        bodies.values().forEach(b -> b.damaged = false);
        checkDefeat();
        if (phase == Phase.RUNNING && bossDefeated) phase = Phase.WON;
        if (phase == Phase.RUNNING && tick >= durationTicks) phase = Phase.LOST;
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
            Body playerTarget=null;
            if(bolt.owner==null){
                for(Body body:bodies.values()){
                    if(body.health<=0)continue;
                    double contact=ArenaMap.circleContact(bolt.x,bolt.y,bolt.dx,bolt.dy,body.x,body.y,RADIUS+2);
                    if(contact<first){first=contact;playerTarget=body;}
                }
            }else{
                for (EnemyBody enemy : enemies) {
                    if (enemy.health <= 0) continue;
                    double contact = ArenaMap.circleContact(bolt.x, bolt.y, bolt.dx, bolt.dy, enemy.x, enemy.y, enemy.radius+2);
                    if (contact < first) { first = contact; target = enemy; }
                }
            }
            boolean hit = Double.isFinite(first);
            bolt.x += bolt.dx * (hit ? first : 1);
            bolt.y += bolt.dy * (hit ? first : 1);
            if(target!=null)damageEnemy(target.id,bolt.owner,bolt.damage);
            if(playerTarget!=null)damage(playerTarget.id,bolt.damage);
            if (hit || --bolt.lifetime == 0) iterator.remove();
        }
        enemies.removeIf(enemy -> enemy.health <= 0);
    }

    void damageEnemy(long enemyId,UUID sourceId,int amount){
        Body source=roster.get(sourceId);
        if(phase!=Phase.RUNNING || source==null || amount<=0)return;
        EnemyBody target=enemies.stream().filter(e->e.id==enemyId && e.health>0).findFirst().orElse(null);
        if(target==null)return;
        source.damage+=Math.min(target.health,amount);
        target.health=Math.max(0,target.health-amount);
        if(target.health==0){
            kills++;source.kills++;gainExperience(10);
            if(target.kind==EnemyKind.BOSS)bossDefeated=true;
        }
    }

    void spawnEnemy(double x, double y) {
        spawnEnemy(EnemyKind.ZOMBIE,x,y);
    }

    void spawnEnemy(EnemyKind kind,double x,double y) {
        Objects.requireNonNull(kind,"kind");
        double radius=kind==EnemyKind.BOSS?10:RADIUS;
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0
                || x > ArenaMap.WIDTH || y > ArenaMap.HEIGHT || ArenaMap.blocked(x, y, radius)) {
            throw new IllegalArgumentException("Enemy must spawn on walkable ground");
        }
        if (phase == Phase.RUNNING && enemies.size() < MAX_ENEMIES && (kind!=EnemyKind.BOSS || !bossSpawned)) {
            int health=switch(kind){case ZOMBIE->60;case RUNNER->45;case SHAMAN->90;case BOSS->12000+6000*(roster.size()-1);};
            enemies.add(new EnemyBody(++nextEntityId,x,y,kind,(int)Math.round(health*difficulty.healthMultiplier()),radius));
            if(kind==EnemyKind.BOSS)bossSpawned=true;
        }
    }

    private EnemyKind nextEnemyKind(){
        int roll=random.nextInt(10);
        if(wave()>=13 && roll<2)return EnemyKind.SHAMAN;
        if(wave()>=7 && roll<5)return EnemyKind.RUNNER;
        return EnemyKind.ZOMBIE;
    }

    private void spawnBoss(){
        if(enemies.size()>=MAX_ENEMIES)enemies.removeLast();
        spawnAwayFromPlayers(EnemyKind.BOSS);
        if(!bossSpawned){
            // Exhaustive fallback guarantees the boss appears even when players cover random spawn candidates.
            for(int row=2;row<ArenaMap.ROWS-2 && !bossSpawned;row++){
                for(int col=2;col<ArenaMap.COLUMNS-2 && !bossSpawned;col++){
                    double x=col*16+8,y=row*16+8;
                    if(!ArenaMap.blocked(x,y,10))spawnEnemy(EnemyKind.BOSS,x,y);
                }
            }
        }
    }

    private void spawnAwayFromPlayers(EnemyKind kind) {
        if (enemies.size() >= MAX_ENEMIES) return;
        for (int attempt = 0; attempt < 80; attempt++) {
            double x = (1 + random.nextInt(ArenaMap.COLUMNS - 2)) * 16 + 8;
            double y = (1 + random.nextInt(ArenaMap.ROWS - 2)) * 16 + 8;
            if (ArenaMap.blocked(x, y, kind==EnemyKind.BOSS?10:RADIUS)) continue;
            if (bodies.values().stream().anyMatch(b -> Math.hypot(x - b.x, y - b.y) < 128)) continue;
            spawnEnemy(kind,x, y);
            return;
        }
    }

    private void advanceEnemies() {
        for (EnemyBody enemy : enemies) {
            int cell = cell(enemy.x, enemy.y);
            Body target = bodies.values().stream().filter(b -> b.health > 0 && b.distances != null)
                    .min(Comparator.comparingInt(b -> (enemy.kind==EnemyKind.BOSS?b.bossDistances:b.distances)[cell])).orElse(null);
            if(target==null)continue;
            if(enemy.action.equals("CHARGE")){
                charge(enemy);
                if(--enemy.actionTicks==0 || enemy.stopped)recover(enemy);
                continue;
            }
            if(enemy.action.equals("WINDUP")){
                if(--enemy.actionTicks==0)attack(enemy);
                continue;
            }
            if(enemy.action.equals("ATTACK") || enemy.action.equals("RECOVER")){
                if(--enemy.actionTicks==0){
                    if(enemy.action.equals("ATTACK"))recover(enemy);
                    else{enemy.action="MOVE";enemy.waypoint=-1;}
                }
                continue;
            }
            double range=switch(enemy.kind){case ZOMBIE->18;case RUNNER->80;case SHAMAN->180;case BOSS->400;};
            double toX=target.x-enemy.x,toY=target.y-enemy.y,distance=Math.hypot(toX,toY);
            // A charge needs to reach the player's body before the wall, not the player's center.
            boolean chargeClear=enemy.kind!=EnemyKind.BOSS
                    || ArenaMap.firstWallContact(enemy.x,enemy.y,toX,toY,enemy.radius)
                    > ArenaMap.circleContact(enemy.x,enemy.y,toX,toY,target.x,target.y,enemy.radius+RADIUS);
            if(distance<=range && chargeClear && clearLine(enemy.x,enemy.y,target.x,target.y,0.5)){
                enemy.aimX=distance>0?toX/distance:1;enemy.aimY=distance>0?toY/distance:0;
                enemy.action="WINDUP";
                enemy.actionTicks=switch(enemy.kind){case ZOMBIE->12;case RUNNER->16;case SHAMAN->28;case BOSS->40;};
                continue;
            }
            int[] path=enemy.kind==EnemyKind.BOSS?target.bossDistances:target.distances;
            if(path[cell]==Integer.MAX_VALUE)continue;
            double speed=switch(enemy.kind){case ZOMBIE->1.5;case RUNNER->2.7;case SHAMAN->1.1;case BOSS->1.8;};
            if (enemy.waypoint < 0 || tick%10==0 || Math.hypot(enemy.x - centerX(enemy.waypoint), enemy.y - centerY(enemy.waypoint)) < speed) {
                enemy.waypoint = cell;
                for (int next : neighbors(cell)) {
                    if (path[next] < path[enemy.waypoint]) enemy.waypoint = next;
                }
            }
            boolean direct = clearLine(enemy.x,enemy.y,target.x,target.y,enemy.radius);
            double dx = (direct ? target.x : centerX(enemy.waypoint)) - enemy.x;
            double dy = (direct ? target.y : centerY(enemy.waypoint)) - enemy.y;
            double scale = speed / Math.max(speed, Math.hypot(dx, dy));
            if (!ArenaMap.blocked(enemy.x + dx * scale, enemy.y, enemy.radius)) enemy.x += dx * scale;
            if (!ArenaMap.blocked(enemy.x, enemy.y + dy * scale, enemy.radius)) enemy.y += dy * scale;
        }
    }

    private int enemyDamage(int base){return (int)Math.round(base*difficulty.damageMultiplier());}

    private void attack(EnemyBody enemy){
        enemy.action=enemy.kind==EnemyKind.RUNNER || enemy.kind==EnemyKind.BOSS?"CHARGE":"ATTACK";
        enemy.actionTicks=switch(enemy.kind){case ZOMBIE->4;case RUNNER->10;case SHAMAN->8;case BOSS->30;};
        enemy.contacted.clear();enemy.stopped=false;
        if(enemy.kind==EnemyKind.SHAMAN && bolts.size()<MAX_PROJECTILES){
            bolts.add(new Bolt(++nextEntityId,null,enemyDamage(16),enemy.x,enemy.y,enemy.aimX*6,enemy.aimY*6));
        }
        if(enemy.kind==EnemyKind.ZOMBIE){
            for(Body body:bodies.values()){
                double dx=body.x-enemy.x,dy=body.y-enemy.y,distance=Math.hypot(dx,dy);
                if(distance<=20 && (distance==0 || (dx*enemy.aimX+dy*enemy.aimY)/distance>=0.1)
                        && clearLine(enemy.x,enemy.y,body.x,body.y,0.5))damage(body.id,enemyDamage(10));
            }
        }
    }

    private void charge(EnemyBody enemy){
        double speed=enemy.kind==EnemyKind.BOSS?12:9,dx=enemy.aimX*speed,dy=enemy.aimY*speed;
        double wall=ArenaMap.firstWallContact(enemy.x,enemy.y,dx,dy,enemy.radius);
        double travel=Double.isFinite(wall)?Math.max(0,wall-0.00001):1;
        for(Body body:bodies.values()){
            double hit=ArenaMap.circleContact(enemy.x,enemy.y,dx*travel,dy*travel,body.x,body.y,enemy.radius+RADIUS);
            if(Double.isFinite(hit) && enemy.contacted.add(body.id))damage(body.id,enemyDamage(enemy.kind==EnemyKind.BOSS?45:18));
        }
        enemy.x+=dx*travel;enemy.y+=dy*travel;
        enemy.stopped=Double.isFinite(wall);
    }

    private static void recover(EnemyBody enemy){
        enemy.action="RECOVER";
        enemy.actionTicks=switch(enemy.kind){case ZOMBIE->20;case RUNNER->26;case SHAMAN->40;case BOSS->55;};
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

    private static int[] distances(double x, double y,double radius) {
        int[] distance = new int[ArenaMap.COLUMNS * ArenaMap.ROWS];
        Arrays.fill(distance, Integer.MAX_VALUE);
        int start = cell(x, y);
        distance[start] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next : neighbors(current)) {
                if (distance[next] != Integer.MAX_VALUE || ArenaMap.blocked(centerX(next), centerY(next), radius)) continue;
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
                down.health = down.loadout.maxHealth()*40/100;
                down.protection = 20;
                down.reviveTicks = 0;
                down.rescuer = null;
            }
        }
    }

    private static boolean clearLine(Body one, Body two) {
        return clearLine(one.x,one.y,two.x,two.y,0.5);
    }

    private static boolean clearLine(double x,double y,double targetX,double targetY,double radius){
        return !Double.isFinite(ArenaMap.firstWallContact(x,y,targetX-x,targetY-y,radius));
    }

    private void checkDefeat() {
        if (phase == Phase.RUNNING && bodies.values().stream().noneMatch(b -> b.health > 0)) phase = Phase.LOST;
    }

    public long tickNumber() { return tick; }
    public Phase phase() { return phase; }
    public int remainingTicks() { return (int) Math.max(0, durationTicks - tick); }
    public int kills() { return kills; }
    public int wave(){return Math.min(30,1+(int)(tick*30/durationTicks));}
    public boolean bossSpawned(){return bossSpawned;}
    public Difficulty difficulty(){return difficulty;}
    public List<Player> players() {
        return bodies.values().stream().map(b -> new Player(b.id, b.x, b.y, b.cooldown / 20.0, b.moving,
                b.health, b.reviveTicks / (double) REVIVE_TICKS,b.loadout.maxHealth(),b.pendingUpgrades,
                b.pendingUpgrades>0?b.choicesMade+1:0,b.offers,b.loadout.snapshot())).toList();
    }
    public List<Enemy> enemies() {
        return enemies.stream().map(e -> new Enemy(e.id,e.x,e.y,e.health,e.maxHealth,e.kind,e.action,e.actionTicks,e.aimX,e.aimY,e.radius)).toList();
    }
    public List<Projectile> projectiles() {
        return bolts.stream().map(b -> new Projectile(b.id,b.x,b.y,b.dx*TICKS_PER_SECOND,b.dy*TICKS_PER_SECOND,b.owner==null)).toList();
    }

    private static final class Body {
        final UUID id;
        double x, y, dx, dy, aimX, aimY;
        int cooldown, fireCooldown, protection, reviveTicks, health = 100;
        long sequence = -1, expires;
        boolean dashRequested, moving, attack, revive, damaged;
        UUID rescuer;
        final Loadout loadout=new Loadout();
        List<Upgrade> offers=List.of();
        int pendingUpgrades,choicesMade,kills,damage,levelReached=1;
        int[] distances,bossDistances;
        Body(UUID id, double x, double y) { this.id = id; this.x = x; this.y = y; }
    }
    private static final class EnemyBody {
        final long id;
        final EnemyKind kind;
        final int maxHealth;
        final double radius;
        final Set<UUID> contacted=new HashSet<>();
        double x,y,aimX=1,aimY;
        int health,waypoint=-1,actionTicks;
        boolean stopped;
        String action="MOVE";
        EnemyBody(long id,double x,double y,EnemyKind kind,int health,double radius){
            this.id=id;this.x=x;this.y=y;this.kind=kind;this.health=this.maxHealth=health;this.radius=radius;
        }
    }
    private static final class Bolt {
        final long id;
        final UUID owner;
        final int damage;
        final double dx, dy;
        double x, y;
        int lifetime = 40;
        Bolt(long id,UUID owner,int damage,double x,double y,double dx,double dy) {
            this.id=id;this.owner=owner;this.damage=damage;this.x=x;this.y=y;this.dx=dx;this.dy=dy;
        }
    }
}
