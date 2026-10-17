package com.gamedead.game;

/** Controlled experience for graphics checks; absent from the shipped client. */
public final class ProgressionFixture {
    private ProgressionFixture(){ }
    public static void gainExperience(ArenaWorld world,int amount){world.gainExperience(amount);}
    public static void defeatBoss(ArenaWorld world,java.util.UUID source){
        var boss=world.enemies().stream().filter(e->e.kind()==EnemyKind.BOSS).findFirst().orElseThrow();
        world.damageEnemy(boss.id(),source,Integer.MAX_VALUE);world.tick();
        if(world.phase()!=ArenaWorld.Phase.WON)throw new AssertionError("Boss defeat did not win the run");
    }
}
