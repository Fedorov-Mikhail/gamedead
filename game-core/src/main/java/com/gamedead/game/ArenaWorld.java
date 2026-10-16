package com.gamedead.game;

import java.util.*;

public final class ArenaWorld {
    public record Player(UUID id, double x, double y, double dashCooldown, boolean moving) { }
    private static final double RADIUS = 5, SPEED_PER_TICK = 4, DASH_DISTANCE = 80;
    private static final int INPUT_LIFETIME = 6, DASH_COOLDOWN = 40;
    private final Map<UUID, Body> bodies = new LinkedHashMap<>();
    private long tick;
    public ArenaWorld(List<UUID> players) {
        if (players.isEmpty() || players.size() > 4 || new HashSet<>(players).size() != players.size()) {
            throw new IllegalArgumentException("World requires 1–4 distinct players");
        }
        double[][] spawns = {{80,192},{560,192},{320,80},{320,304}};
        for(int i=0;i<players.size();i++) bodies.put(players.get(i),new Body(players.get(i),spawns[i][0],spawns[i][1]));
    }
    public void input(UUID player, long sequence, double x, double y, boolean dash) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x)>1 || Math.abs(y)>1 || sequence<0) {
            throw new IllegalArgumentException("Invalid movement input");
        }
        Body body=bodies.get(player);
        if(body==null || sequence<=body.sequence) return;
        body.sequence=sequence;
        double length=Math.max(1,Math.hypot(x,y));
        body.dx=x/length; body.dy=y/length;
        body.expires=tick+INPUT_LIFETIME;
        body.dashRequested |= dash;
    }
    public void clearInput(UUID player) {
        Body body=bodies.get(player);
        if(body!=null) {body.dx=0;body.dy=0;body.dashRequested=false;body.moving=false;}
    }
    public void resetConnection(UUID player) {
        clearInput(player);
        Body body=bodies.get(player);
        if(body!=null) body.sequence=-1;
    }
    public void remove(UUID player) { bodies.remove(player); }
    public void tick() {
        tick++;
        for(Body body:bodies.values()) {
            if(body.cooldown>0) body.cooldown--;
            if(tick>body.expires) clearInput(body.id);
            double previousX=body.x, previousY=body.y;
            if(body.dashRequested && body.cooldown==0 && Math.hypot(body.dx,body.dy)>0) {
                move(body,body.dx*DASH_DISTANCE,body.dy*DASH_DISTANCE);
                body.cooldown=DASH_COOLDOWN;
            }
            body.dashRequested=false;
            move(body,body.dx*SPEED_PER_TICK,body.dy*SPEED_PER_TICK);
            body.moving=body.x!=previousX || body.y!=previousY;
        }
    }
    private void move(Body body,double dx,double dy) {
        int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dy))/2));
        for(int i=0;i<steps;i++) {
            double x=body.x+dx/steps, y=body.y+dy/steps;
            if(!ArenaMap.blocked(x,body.y,RADIUS)) body.x=x;
            if(!ArenaMap.blocked(body.x,y,RADIUS)) body.y=y;
        }
    }
    public long tickNumber() { return tick; }
    public List<Player> players() {
        return bodies.values().stream().map(b->new Player(b.id,b.x,b.y,b.cooldown/20.0,b.moving)).toList();
    }
    private static final class Body {
        final UUID id;
        double x,y,dx,dy;
        int cooldown;
        long sequence=-1,expires;
        boolean dashRequested,moving;
        Body(UUID id,double x,double y){this.id=id;this.x=x;this.y=y;}
    }
}
