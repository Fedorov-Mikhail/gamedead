package com.gamedead.client.ui;

/** Pure interpolation checks, also runnable without a graphics context. */
final class ProjectileMotionCheck {
    public static void main(String[] args){
        ProjectileMotion shot=new ProjectileMotion(10,20);
        shot.accept(30,40);shot.update(0.05);
        close(shot.x(),20);close(shot.y(),30);
        shot.accept(40,50);close(shot.x(),20);close(shot.y(),30);
        shot.update(0.05);close(shot.x(),30);close(shot.y(),40);
        shot.accept(40,50);shot.update(0.025);close(shot.x(),35);close(shot.y(),45);
        shot.update(10);close(shot.x(),40);close(shot.y(),50);
        shot.update(-1);close(shot.x(),40);close(shot.y(),50);
    }
    private static void close(double actual,double expected){
        if(Math.abs(actual-expected)>0.00001)throw new AssertionError("Expected "+expected+", got "+actual);
    }
}
