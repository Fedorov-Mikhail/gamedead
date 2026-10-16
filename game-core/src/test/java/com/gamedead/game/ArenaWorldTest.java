package com.gamedead.game;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ArenaWorldTest {
    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ArenaWorld world = new ArenaWorld(List.of(id));
    private ArenaWorld.Player player() { return world.players().getFirst(); }
    @Test void commandsCannotMovePlayerUntilServerTick() {
        double x = player().x();
        world.input(id, 1, 1, 0, false);
        assertThat(player().x()).isEqualTo(x);
        world.tick();
        assertThat(player().x()).isEqualTo(x + 4);
    }
    @Test void diagonalSpeedIsNormalizedAndInputStopsWhenStale() {
        double x = player().x(), y = player().y();
        world.input(id, 1, 1, 1, false);
        world.tick();
        assertThat(Math.hypot(player().x()-x,player().y()-y)).isCloseTo(4, within(0.0001));
        for(int i=0;i<20;i++) world.tick();
        double stopped = player().x();
        world.tick();
        assertThat(player().x()).isEqualTo(stopped);
    }
    @Test void floodAndOldSequencesCannotAccelerateMovement() {
        double x = player().x();
        for(int i=1;i<=100;i++) world.input(id,i,1,0,false);
        world.input(id, 50, -1, 0, false);
        world.tick();
        assertThat(player().x()).isEqualTo(x+4);
    }
    @Test void playerCannotCrossBorderEvenByDashing() {
        for(int i=0;i<100;i++) { world.input(id,i+1,-1,0,true); world.tick(); }
        assertThat(player().x()).isGreaterThanOrEqualTo(21);
        assertThat(ArenaMap.blocked(player().x(),player().y(),5)).isFalse();
    }
    @Test void dashHasCooldownAndCannotRepeatFromHeldInput() {
        double x = player().x();
        world.input(id,1,1,0,true); world.tick();
        assertThat(player().x()-x).isGreaterThan(60);
        assertThat(player().dashCooldown()).isGreaterThan(1.8);
        x=player().x();
        world.tick();
        assertThat(player().x()-x).isEqualTo(4);
        x=player().x();
        world.input(id,2,1,0,true); world.tick();
        assertThat(player().x()-x).isEqualTo(4);
    }
    @Test void centralObstacleBlocksMovementAndDash() {
        for(int i=0;i<100;i++) { world.input(id,i+1,1,0,true); world.tick(); }
        assertThat(player().x()).isLessThanOrEqualTo(283);
        assertThat(ArenaMap.blocked(player().x(),player().y(),5)).isFalse();
    }
    @Test void invalidInputNeverPoisonsWorldAndPlayersRemainIndependent() {
        assertThatIllegalArgumentException().isThrownBy(() -> world.input(id,1,Double.NaN,0,false));
        assertThatIllegalArgumentException().isThrownBy(() -> world.input(id,1,Double.POSITIVE_INFINITY,0,false));
        assertThatIllegalArgumentException().isThrownBy(() -> world.input(id,1,2,0,false));
        world.input(UUID.randomUUID(),1,1,0,false);
        world.tick();
        assertThat(player().x()).isFinite();
        world.remove(id);
        assertThat(world.players()).isEmpty();
    }
}
