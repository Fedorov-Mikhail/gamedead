package com.gamedead.game;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SurvivalTest {
    private final UUID one=UUID.randomUUID(),two=UUID.randomUUID();

    @Test void defaultRunLastsThirtyMinutesAndDifficultyScalesOriginalRoster() {
        ArenaWorld normal=new ArenaWorld(List.of(one,two));
        assertThat(normal.remainingTicks()).isEqualTo(36_000);
        assertThat(normal.difficulty()).isEqualTo(Difficulty.NORMAL);
        ArenaWorld easy=new ArenaWorld(List.of(one,two),1800,Difficulty.EASY);
        ArenaWorld hard=new ArenaWorld(List.of(one,two),1800,Difficulty.HARD);
        hard.remove(two);
        easy.spawnEnemy(EnemyKind.BOSS,400,80);hard.spawnEnemy(EnemyKind.BOSS,400,80);
        assertThat(easy.enemies().getFirst().maxHealth()).isEqualTo(13_500);
        assertThat(hard.enemies().getFirst().maxHealth()).isEqualTo(26_100);
        assertThat(hard.enemies().getFirst().radius()).isGreaterThan(5);
    }

    @Test void bossSpawnsAtNinetyPercentEvenAtCapacityAndOnlyItsDeathWins() {
        ArenaWorld world=new ArenaWorld(List.of(one),1);
        for(int i=0;i<40;i++)world.spawnEnemy(400,80);
        for(int i=0;i<17;i++)world.tick();
        assertThat(world.bossSpawned()).isFalse();
        world.tick();
        assertThat(world.bossSpawned()).isTrue();
        assertThat(world.enemies()).hasSize(40);
        var boss=world.enemies().stream().filter(e->e.kind()==EnemyKind.BOSS).findFirst().orElseThrow();
        assertThat(ArenaMap.blocked(boss.x(),boss.y(),boss.radius())).isFalse();
        world.damageEnemy(boss.id(),one,100_000);world.tick();
        assertThat(world.phase()).isEqualTo(ArenaWorld.Phase.WON);
        assertThat(world.results().getFirst().damage()).isEqualTo(boss.maxHealth());
        assertThat(world.totalExperience()).isEqualTo(10);

        ArenaWorld defeat=new ArenaWorld(List.of(one),1);
        for(int i=0;i<18;i++)defeat.tick();
        defeat.damageEnemy(defeat.enemies().getFirst().id(),one,100_000);
        defeat.damage(one,100);defeat.tick();
        assertThat(defeat.phase()).isEqualTo(ArenaWorld.Phase.LOST);
    }

    @Test void runnerLocksTelegraphAndBossChargeCannotCrossCentralWall() {
        ArenaWorld runner=new ArenaWorld(List.of(one));runner.spawnEnemy(EnemyKind.RUNNER,140,192);
        runner.tick();var start=runner.enemies().getFirst();
        assertThat(start.action()).isEqualTo("WINDUP");
        for(int i=0;i<16;i++){runner.input(one,i+1,0,1,false);runner.tick();}
        var charge=runner.enemies().getFirst();
        assertThat(charge.action()).isEqualTo("CHARGE");
        assertThat(charge.aimX()).isEqualTo(start.aimX());
        assertThat(charge.aimY()).isEqualTo(start.aimY());

        ArenaWorld boss=new ArenaWorld(List.of(one));boss.spawnEnemy(EnemyKind.BOSS,140,192);
        boss.tick();assertThat(boss.enemies().getFirst().action()).isEqualTo("WINDUP");
        for(int i=0;i<70;i++){
            boss.tick();var enemy=boss.enemies().getFirst();
            assertThat(ArenaMap.blocked(enemy.x(),enemy.y(),enemy.radius())).isFalse();
            assertThat(enemy.x()).isGreaterThanOrEqualTo(26);
        }

        ArenaWorld obstacle=new ArenaWorld(List.of(one));obstacle.spawnEnemy(EnemyKind.BOSS,400,192);
        for(int i=0;i<400;i++){
            obstacle.tick();var enemy=obstacle.enemies().getFirst();
            assertThat(ArenaMap.blocked(enemy.x(),enemy.y(),enemy.radius())).isFalse();
        }
        assertThat(obstacle.enemies().getFirst().x()).isLessThan(270);
    }

    @Test void shamansFireHostileOrbsAfterWarningAndWallsStopThem() {
        ArenaWorld world=new ArenaWorld(List.of(one));world.spawnEnemy(EnemyKind.SHAMAN,140,192);
        world.tick();assertThat(world.projectiles()).isEmpty();
        assertThat(world.enemies().getFirst().action()).isEqualTo("WINDUP");
        for(int i=0;i<28;i++)world.tick();
        assertThat(world.projectiles()).hasSize(1).allSatisfy(p->{
            assertThat(p.hostile()).isTrue();assertThat(p.velocityX()).isEqualTo(-120);
        });
        for(int i=0;i<10;i++)world.tick();
        assertThat(world.players().getFirst().health()).isLessThan(100);
        assertThat(world.enemies().getFirst().health()).isEqualTo(world.enemies().getFirst().maxHealth());

        ArenaWorld wall=new ArenaWorld(List.of(one));wall.spawnEnemy(EnemyKind.SHAMAN,220,192);
        wall.tick();for(int i=0;i<28;i++)wall.tick();
        assertThat(wall.projectiles()).hasSize(1);
        // The orb keeps its locked horizontal course when the player leaves that line.
        for(int i=0;i<45;i++){wall.input(one,i+1,0,-1,false);wall.tick();}
        assertThat(wall.projectiles()).noneMatch(p->p.hostile());
        assertThat(wall.players().getFirst().health()).isEqualTo(100);
    }

    @Test void bossCanTelegraphAndHitPlayerBesideWallWithoutCrossingIt(){
        ArenaWorld world=new ArenaWorld(List.of(one));long sequence=0;
        for(int i=0;i<28;i++){world.input(one,++sequence,0,-1,false);world.tick();}
        for(int i=0;i<14;i++){world.input(one,++sequence,-1,0,false);world.tick();}
        world.input(one,++sequence,-0.75,0,false);world.tick();world.clearInput(one);
        assertThat(world.players().getFirst().x()).isEqualTo(21);
        world.spawnEnemy(EnemyKind.BOSS,100,80);world.tick();
        assertThat(world.enemies().getFirst().action()).isEqualTo("WINDUP");
        for(int i=0;i<50;i++)world.tick();
        assertThat(world.players().getFirst().health()).isEqualTo(55);
        var boss=world.enemies().getFirst();
        assertThat(boss.x()).isGreaterThanOrEqualTo(26);
        assertThat(ArenaMap.blocked(boss.x(),boss.y(),boss.radius())).isFalse();
    }

    @Test void wavesIntroduceRunnerThenShamanAndHardModeSpawnsFaster() {
        int easy=observeWaves(Difficulty.EASY),hard=observeWaves(Difficulty.HARD);
        assertThat(hard).isGreaterThan(easy);
    }

    private int observeWaves(Difficulty difficulty){
        ArenaWorld world=new ArenaWorld(List.of(one),100,difficulty);
        Set<Long> seen=new HashSet<>();Set<EnemyKind> kinds=new HashSet<>();
        for(int i=0;i<1700;i++){
            world.tick();
            for(var enemy:world.enemies()){
                seen.add(enemy.id());kinds.add(enemy.kind());
                if(world.wave()<7)assertThat(enemy.kind()).isEqualTo(EnemyKind.ZOMBIE);
                if(world.wave()<13)assertThat(enemy.kind()).isNotEqualTo(EnemyKind.SHAMAN);
                world.damageEnemy(enemy.id(),one,100_000);
            }
        }
        assertThat(kinds).containsExactlyInAnyOrder(EnemyKind.ZOMBIE,EnemyKind.RUNNER,EnemyKind.SHAMAN);
        assertThat(world.wave()).isEqualTo(26);
        assertThat(world.bossSpawned()).isFalse();
        return seen.size();
    }
}
