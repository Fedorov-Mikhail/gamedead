package com.gamedead.protocol.world;
public record EnemySnapshot(long id,double x,double y,int health,int maxHealth,String kind,String action,
                            int actionTicks,double aimX,double aimY,double radius) {
    public EnemySnapshot(long id,double x,double y,int health){
        this(id,x,y,health,Math.max(60,health),"ZOMBIE","MOVE",0,0,0,5);
    }
}
