package com.gamedead.server.room;

import com.gamedead.server.match.MatchJournal;
import com.gamedead.server.support.IntegrationSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties="gamedead.match-duration-seconds=1")
class RoomMatchIT extends IntegrationSupport {
    @Autowired RoomRegistry rooms;
    @Autowired MatchJournal journal;
    @Autowired JdbcTemplate jdbc;

    @Test void authenticatedRoomStartFinishAndHistoryUseSameDurableMatchAndAward() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,8);
        UUID one=UUID.fromString(field(register("one_"+suffix).body(),"accountId"));
        UUID two=UUID.fromString(field(register("two_"+suffix).body(),"accountId"));
        String first=field(login("one_"+suffix).body(),"accessToken"),second=field(login("two_"+suffix).body(),"accessToken");
        UUID connectionOne=UUID.randomUUID(),connectionTwo=UUID.randomUUID();
        RoomMailbox inbox=rooms.connect(one,"First Mage",connectionOne);rooms.connect(two,"Second Mage",connectionTwo);
        try {
            var created=request("POST","/api/v1/rooms/create","",first);assertThat(created.statusCode()).isEqualTo(201);
            String code=JSON.readTree(created.body()).path("room").path("code").asString();
            assertThat(request("POST","/api/v1/rooms/join","{\"code\":\""+code+"\"}",second).statusCode()).isEqualTo(200);
            assertThat(request("POST","/api/v1/rooms/ready","{\"ready\":true}",first).statusCode()).isEqualTo(200);
            assertThat(request("POST","/api/v1/rooms/ready","{\"ready\":true}",second).statusCode()).isEqualTo(200);
            assertThat(request("POST","/api/v1/rooms/start","",first).statusCode()).isEqualTo(200);
            com.gamedead.protocol.world.WorldSnapshot world=null;com.gamedead.protocol.room.RoomEvent event;
            while((event=inbox.poll())!=null)if(event.world()!=null)world=event.world();
            UUID match=Objects.requireNonNull(world).worldId();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM game.match_players WHERE match_id=?",Integer.class,match)).isEqualTo(2);
            var forged=request("POST","/api/v1/rooms/upgrade",JSON.writeValueAsString(Map.of("worldId",match,"choiceId",1,"upgrade","POWER")),first);
            assertThat(forged.statusCode()).isEqualTo(409);assertThat(forged.body()).contains("INVALID_UPGRADE");
            for(int i=0;i<20;i++)rooms.tick();journal.flush();journal.flush();
            assertThat(jdbc.queryForObject("SELECT status FROM game.matches WHERE id=?",String.class,match)).isEqualTo("LOST");
            var history=request("GET","/api/v1/matches/me",null,first);assertThat(history.statusCode()).isEqualTo(200);
            var result=JSON.readTree(history.body()).path("matches").get(0);
            assertThat(result.path("matchId").asString()).isEqualTo(match.toString());
            assertThat(result.path("accountXp").asLong()).isZero();
            assertThat(jdbc.queryForObject("SELECT account_xp FROM game.player_profiles WHERE account_id=?",Long.class,one)).isZero();
            assertThat(jdbc.queryForObject("SELECT account_xp FROM game.player_profiles WHERE account_id=?",Long.class,two)).isZero();
        } finally {
            rooms.leave(one);rooms.leave(two);rooms.disconnect(one,connectionOne);rooms.disconnect(two,connectionTwo);journal.flush();
        }
    }
}
