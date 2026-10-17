package com.gamedead.protocol.world;
public record ProjectileSnapshot(long id,double x,double y,double velocityX,double velocityY,boolean hostile) {
    public ProjectileSnapshot(long id,double x,double y){this(id,x,y,0,0,false);}
}
