package com.gamedead.client.net;

import com.gamedead.protocol.world.*;
import com.gamedead.protocol.match.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProgressionMessagesTest {
    private PlayerSnapshot player(int health,int maximum,int pending,List<String> offers,Map<String,Integer> ranks){
        return new PlayerSnapshot(UUID.randomUUID(),80,192,0,false,health,0,maximum,pending,pending>0?ranks.values().stream().mapToInt(Integer::intValue).sum()+1:0,offers,ranks);
    }
    private WorldSnapshot world(PlayerSnapshot p,int level,int xp,int required,String phase,String save){
        return new WorldSnapshot("ABC234",1,UUID.randomUUID(),10,List.of(p),List.of(),List.of(),phase,4790,0,level,xp,required,save);
    }
    @Test void acceptsRealProgressionAndRejectsInvalidHealthOffersRanksXpAndSaveState(){
        PlayerSnapshot p=player(125,125,1,List.of("POWER","HASTE","VITALITY"),Map.of("VITALITY",1));
        assertThat(RoomMessages.valid(world(p,3,8,40,"RUNNING","RUNNING"))).isTrue();
        assertThat(RoomMessages.valid(world(player(126,125,0,List.of(),Map.of()),2,8,40,"RUNNING","RUNNING"))).isFalse();
        assertThat(RoomMessages.valid(world(player(100,100,0,List.of("POWER"),Map.of()),2,8,40,"RUNNING","RUNNING"))).isFalse();
        assertThat(RoomMessages.valid(world(player(100,100,1,List.of("POWER","POWER","HASTE"),Map.of()),2,8,40,"RUNNING","RUNNING"))).isFalse();
        assertThat(RoomMessages.valid(world(player(100,100,1,List.of("POWER","HASTE","DASH"),Map.of("POWER",5)),2,8,40,"RUNNING","RUNNING"))).isFalse();
        assertThat(RoomMessages.valid(world(player(100,100,0,List.of(),Map.of("UNKNOWN",1)),2,8,40,"RUNNING","RUNNING"))).isFalse();
        assertThat(RoomMessages.valid(world(p,2,40,40,"RUNNING","RUNNING"))).isFalse();
        assertThat(RoomMessages.valid(world(p,11,0,0,"RUNNING","RUNNING"))).isFalse();
        assertThat(RoomMessages.valid(world(p,2,8,40,"WON","UNKNOWN"))).isFalse();
        assertThat(RoomMessages.valid(world(p,3,8,40,"WON","SAVED"))).isTrue();
    }
    @Test void historyHasOnlyBoundedFinishedMatchesAndKnownBuilds(){
        var now=Instant.parse("2026-10-17T10:00:00Z");
        var match=new MatchSummary(UUID.randomUUID(),"WON",now.minusSeconds(240),now,240,4,50,20,500,80,Map.of("POWER",2));
        assertThat(RoomMessages.valid(new MatchHistoryResponse(List.of(match)))).isTrue();
        assertThat(RoomMessages.valid(new MatchHistoryResponse(Collections.nCopies(21,match)))).isFalse();
        assertThat(RoomMessages.valid(new MatchHistoryResponse(List.of(new MatchSummary(UUID.randomUUID(),"RUNNING",now,now,0,1,0,0,0,0,Map.of()))))).isFalse();
        assertThat(RoomMessages.valid(new MatchHistoryResponse(List.of(new MatchSummary(UUID.randomUUID(),"WON",now,now,0,1,0,0,0,0,Map.of("POWER",6)))))).isFalse();
    }
    @Test void acceptsBossAndHostileShotsButRejectsForgedCombatMetadata(){
        var p=player(100,100,0,List.of(),Map.of());
        var boss=new EnemySnapshot(1,320,80,20_000,20_000,"BOSS","WINDUP",20,1,0,12);
        var shot=new ProjectileSnapshot(2,300,80,-100,0,true);
        var valid=new WorldSnapshot("ABC234",1,UUID.randomUUID(),100,List.of(p),List.of(boss),List.of(shot),"RUNNING",500,0,1,0,60,"RUNNING",28,true);
        assertThat(RoomMessages.valid(valid)).isTrue();
        var forged=new EnemySnapshot(1,320,80,20_000,20_000,"BOSS","WINDUP",20,Double.NaN,0,12);
        assertThat(RoomMessages.valid(new WorldSnapshot("ABC234",1,valid.worldId(),100,List.of(p),List.of(forged),List.of(shot),"RUNNING",500,0,1,0,60,"RUNNING",28,true))).isFalse();
        assertThat(RoomMessages.valid(new WorldSnapshot("ABC234",1,valid.worldId(),100,List.of(p),List.of(boss),List.of(shot),"RUNNING",500,0,1,0,60,"RUNNING",28,false))).isFalse();
    }
}
