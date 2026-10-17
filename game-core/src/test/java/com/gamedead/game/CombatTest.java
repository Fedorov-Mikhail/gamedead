package com.gamedead.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CombatTest {
    private final UUID one = UUID.randomUUID(), two = UUID.randomUUID();

    @Test void shotsAreServerLimitedAndOldCommandsCannotFireAgain() {
        ArenaWorld world = new ArenaWorld(List.of(one));
        world.input(one, 1, 0, 0, false, 1, 0, true, false);
        world.tick();
        assertThat(world.projectiles()).hasSize(1);
        for (int i = 0; i < 5; i++) {
            world.input(one, 1, 0, 0, false, 1, 0, true, false);
            world.tick();
        }
        assertThat(world.projectiles()).hasSize(1);
        for (int i = 0; i < 5; i++) world.tick();
        assertThat(world.projectiles()).hasSize(1); // Stale attack stops with stale movement.
    }

    @Test void sweptShotsHitOneEnemyAndNeverCrossWallsOrDamageAllies() {
        ArenaWorld world = new ArenaWorld(List.of(one, two));
        world.spawnEnemy(96, 192);
        world.spawnEnemy(105, 192);
        world.input(one, 1, 0, 0, false, 1, 0, true, false);
        world.tick();
        assertThat(world.enemies().getFirst().health()).isEqualTo(40);
        assertThat(world.enemies().get(1).health()).isEqualTo(60);
        assertThat(world.projectiles()).isEmpty();
        assertThat(world.players()).allMatch(p -> p.health() == 100);

        ArenaWorld wall = new ArenaWorld(List.of(one));
        wall.input(one, 1, 0, 0, false, -1, 0, true, false);
        for (int i = 0; i < 8; i++) wall.tick();
        assertThat(wall.projectiles()).isEmpty();
    }

    @Test void damageDownsPlayerAndWorldLosesBeforeTimerCanWin() {
        ArenaWorld world = new ArenaWorld(List.of(one), 1);
        for (int i = 0; i < 19; i++) world.tick();
        world.damage(one, 100);
        world.tick();
        assertThat(world.phase()).isEqualTo(ArenaWorld.Phase.LOST);
        long tick = world.tickNumber();
        world.tick();
        assertThat(world.tickNumber()).isEqualTo(tick);
    }

    @Test void timerExpiresWithoutVictoryAndFinishedWorldRejectsMovementAndSpawning() {
        ArenaWorld world = new ArenaWorld(List.of(one), 1);
        for (int i = 0; i < 20; i++) world.tick();
        assertThat(world.phase()).isEqualTo(ArenaWorld.Phase.LOST);
        double x = world.players().getFirst().x();
        world.input(one, 1, 1, 0, true, 1, 0, true, false);
        world.tick();
        assertThat(world.players().getFirst().x()).isEqualTo(x);
        assertThat(world.remainingTicks()).isZero();
    }

    @Test void allyRevivesOnlyAfterThreeSecondsAndDamageResetsProgress() {
        ArenaWorld world = new ArenaWorld(List.of(one, two));
        long sequence = 0;
        for (int i = 0; i < 24; i++) { world.input(two, ++sequence, 0, -1, false); world.tick(); }
        for (int i = 0; i < 120; i++) { world.input(two, ++sequence, -1, 0, false); world.tick(); }
        for (int i = 0; i < 24; i++) { world.input(two, ++sequence, 0, 1, false); world.tick(); }
        world.damage(one, 100);
        for (int i = 0; i < 30; i++) {
            world.input(two, ++sequence, 0, 0, false, 0, 0, false, true); world.tick();
        }
        assertThat(world.players().getFirst().health()).isZero();
        assertThat(world.players().getFirst().reviveProgress()).isCloseTo(0.5, within(0.01));
        world.damage(two, 10);
        world.tick();
        assertThat(world.players().getFirst().reviveProgress()).isZero();
        for (int i = 0; i < 60; i++) {
            world.input(two, ++sequence, 0, 0, false, 0, 0, false, true); world.tick();
        }
        assertThat(world.players().getFirst().health()).isEqualTo(40);
        assertThat(world.phase()).isEqualTo(ArenaWorld.Phase.RUNNING);
        assertThatIllegalArgumentException().isThrownBy(() -> world.input(one, 100, 0, 0, false, Double.NaN, 0, true, false));
    }

    @Test void enemyRoutesAroundCentralWallAndContactDamageHasGracePeriod() {
        ArenaWorld world = new ArenaWorld(List.of(one));
        world.spawnEnemy(400, 192);
        long enemy = world.enemies().getFirst().id();
        for (int i = 0; i < 400; i++) world.tick();
        var tracked = world.enemies().stream().filter(e -> e.id() == enemy).findFirst().orElseThrow();
        assertThat(tracked.x()).isLessThan(270);
        assertThat(ArenaMap.blocked(tracked.x(), tracked.y(), 5)).isFalse();

        ArenaWorld contact = new ArenaWorld(List.of(one));
        contact.spawnEnemy(80, 192);
        contact.tick();
        assertThat(contact.enemies().getFirst().action()).isEqualTo("WINDUP");
        assertThat(contact.players().getFirst().health()).isEqualTo(100);
        for (int i = 0; i < 12; i++) contact.tick();
        int health = contact.players().getFirst().health();
        contact.tick();
        assertThat(health).isEqualTo(90);
        assertThat(contact.players().getFirst().health()).isEqualTo(health);
    }

    @Test void spawnsAndProjectilesStayBoundedAndAvoidPlayersAndWalls() {
        ArenaWorld world = new ArenaWorld(List.of(one));
        for (int i = 0; i < 100; i++) world.spawnEnemy(400, 80);
        assertThat(world.enemies()).hasSize(40);
        assertThatIllegalArgumentException().isThrownBy(() -> world.spawnEnemy(300, 180));
        assertThatIllegalArgumentException().isThrownBy(() -> new ArenaWorld(List.of(one), 0));
        world.abort();
        assertThat(world.phase()).isEqualTo(ArenaWorld.Phase.ABORTED);
    }
    @Test void killedEnemyDisappearsAndIsCountedOnce() {
        ArenaWorld world=new ArenaWorld(List.of(one));world.spawnEnemy(112,192);
        for(int i=0;i<30;i++){
            world.input(one,i+1,0,0,false,1,0,true,false);world.tick();
        }
        assertThat(world.kills()).isEqualTo(1);
        assertThat(world.enemies()).isEmpty();
    }
    @Test void automaticSpawnsHaveSpaceAndDownedPlayersCannotAttackOrMove() {
        ArenaWorld world=new ArenaWorld(List.of(one,two));
        for(int i=0;i<200;i++)world.tick();
        assertThat(world.enemies()).hasSize(2).allSatisfy(enemy->{
            assertThat(ArenaMap.blocked(enemy.x(),enemy.y(),5)).isFalse();
            assertThat(world.players()).allMatch(p->Math.hypot(p.x()-enemy.x(),p.y()-enemy.y())>=126);
        });
        world.damage(one,100);double x=world.players().getFirst().x();
        world.input(one,1,1,0,true,1,0,true,true);world.tick();
        assertThat(world.players().getFirst().x()).isEqualTo(x);
        assertThat(world.projectiles()).isEmpty();
        world.remove(two);
        assertThat(world.phase()).isEqualTo(ArenaWorld.Phase.LOST);
    }
    @Test void oneRescuerCannotReviveTwoPlayersAtOnce() {
        UUID third=UUID.randomUUID();ArenaWorld world=new ArenaWorld(List.of(one,two,third));
        for(int i=0;i<168;i++){
            world.input(two,i+1,i<24?0:i<144?-1:0,i<24?-1:i<144?0:1,false);
            world.input(third,i+1,i<60?-1:0,i>=60 && i<88?1:0,false);
            world.tick();
        }
        world.damage(one,100);world.damage(third,100);
        for(int i=0;i<60;i++){
            world.input(two,169+i,0,0,false,0,0,false,true);world.tick();
        }
        assertThat(world.players().stream().filter(p->!p.id().equals(two) && p.health()>0).count()).isEqualTo(1);
    }
    @Test void projectileHitsNearestIntersectionRegardlessOfEnemySpawnOrder() {
        ArenaWorld world=new ArenaWorld(List.of(one));
        world.spawnEnemy(100,192);world.spawnEnemy(99.5,192);
        world.input(one,1,0,0,false,1,0,true,false);world.tick();
        assertThat(world.enemies().getFirst().health()).isEqualTo(60);
        assertThat(world.enemies().get(1).health()).isEqualTo(40);
    }
    @Test void projectileDetectsGrazingContactBetweenSamples() {
        ArenaWorld world=new ArenaWorld(List.of(one));world.spawnEnemy(91,198.99);
        world.input(one,1,0,0,false,1,0,true,false);world.tick();
        assertThat(world.enemies().getFirst().health()).isEqualTo(40);
        assertThat(world.projectiles()).isEmpty();
    }
    @Test void wallsWinBeforeAnEnemyAndRoundedCornersHaveContinuousContact() {
        ArenaWorld world=new ArenaWorld(List.of(one));world.spawnEnemy(400,192);
        world.input(one,1,0,0,false,1,0,true,false);
        for(int i=0;i<20;i++)world.tick();
        assertThat(world.enemies().getFirst().health()).isEqualTo(60);
        assertThat(world.projectiles()).isEmpty();
        double expected=(8-Math.sqrt(4-1.99*1.99))/16;
        assertThat(ArenaMap.firstWallContact(280,158.01,16,0,2)).isCloseTo(expected,within(0.000001));
    }
}
