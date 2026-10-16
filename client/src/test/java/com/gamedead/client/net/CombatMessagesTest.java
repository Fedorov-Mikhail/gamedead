package com.gamedead.client.net;

import com.gamedead.protocol.world.*;
import com.gamedead.protocol.room.RoomEvent;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CombatMessagesTest {
    private final UUID player = UUID.randomUUID(), world = UUID.randomUUID();
    private WorldSnapshot snapshot(int health, String phase, List<EnemySnapshot> enemies, List<ProjectileSnapshot> shots) {
        return new WorldSnapshot("ABC234", 1, world, 10,
                List.of(new PlayerSnapshot(player, 80, 192, 0, false, health, 0)), enemies, shots, phase, 4790, 0);
    }
    @Test void combatEntitiesAndFinishedResultHaveBoundedValidatedValues() {
        assertThat(RoomMessages.valid(snapshot(100, "RUNNING", List.of(new EnemySnapshot(1,200,80,60)),
                List.of(new ProjectileSnapshot(2,100,192))))).isTrue();
        assertThat(RoomMessages.valid(snapshot(-1,"RUNNING",List.of(),List.of()))).isFalse();
        assertThat(RoomMessages.valid(snapshot(100,"UNKNOWN",List.of(),List.of()))).isFalse();
        assertThat(RoomMessages.valid(snapshot(100,"RUNNING",List.of(new EnemySnapshot(1,Double.NaN,80,60)),List.of()))).isFalse();
        assertThat(RoomMessages.valid(snapshot(100,"RUNNING",List.of(),Collections.nCopies(129,new ProjectileSnapshot(1,80,192))))).isFalse();
        assertThat(RoomMessages.valid(snapshot(100,"ABORTED",List.of(),List.of()))).isTrue();
    }
    @Test void maximumSceneSurvivesJsonRoundTripWithinSocketMessageBudget() {
        List<EnemySnapshot> enemies=new ArrayList<>();
        List<ProjectileSnapshot> shots=new ArrayList<>();
        for(int i=1;i<=40;i++)enemies.add(new EnemySnapshot(i,200,80,60));
        for(int i=41;i<=168;i++)shots.add(new ProjectileSnapshot(i,100,192));
        List<PlayerSnapshot> players=new ArrayList<>();
        for(int i=0;i<4;i++)players.add(new PlayerSnapshot(UUID.randomUUID(),80+i*20,192,2,true,100,0));
        var state=new WorldSnapshot("ABC234",1,world,200,players,enemies,shots,"RUNNING",4600,0);
        JsonMapper json=JsonMapper.builder().build();
        String wire=json.writeValueAsString(RoomEvent.world(state));
        assertThat(wire.getBytes(StandardCharsets.UTF_8).length).isLessThan(65_536);
        assertThat(RoomMessages.valid(json.readValue(wire,RoomEvent.class).world())).isTrue();
    }
}
