package com.gamedead.client.ui;

/** Interpolates only between received positions; never predicts through unseen walls. */
final class ProjectileMotion {
    private double startX,startY,targetX,targetY,elapsed;
    ProjectileMotion(double x,double y){startX=targetX=x;startY=targetY=y;}
    void accept(double x,double y){if(x==targetX && y==targetY)return;startX=x();startY=y();targetX=x;targetY=y;elapsed=0;}
    void update(double delta){elapsed=Math.min(0.1,elapsed+Math.max(0,delta));}
    double x(){return startX+(targetX-startX)*elapsed/0.1;}
    double y(){return startY+(targetY-startY)*elapsed/0.1;}
}
