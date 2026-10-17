package com.gamedead.server.match;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class MatchStoreValidationTest {
    private final MatchStore store=new MatchStore(mock(JdbcTemplate.class),JsonMapper.builder().build(),Clock.systemUTC());
    @Test void rejectsInvalidStartRosterAndDurationBeforeDatabaseAccess() {
        var start=MatchJournalTest.start();
        assertThatThrownBy(()->store.begin(new MatchStart(start.id(),start.startedAt(),0,start.players()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.begin(new MatchStart(start.id(),start.startedAt(),1801,start.players()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.begin(new MatchStart(start.id(),start.startedAt(),60,List.of(start.players().getFirst())))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.begin(new MatchStart(start.id(),start.startedAt(),60,List.of(start.players().getFirst(),start.players().getFirst())))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsSupplementaryUnicodeNamesWithinCodePointLimit() {
        var jdbc=mock(JdbcTemplate.class);
        var store=new MatchStore(jdbc,JsonMapper.builder().build(),Clock.systemUTC());
        var start=MatchJournalTest.start();
        var named=new MatchStart(start.id(),start.startedAt(),60,List.of(
            new MatchStart.Participant(start.players().getFirst().accountId(),"🧙".repeat(13)),start.players().getLast()));
        when(jdbc.update(anyString(),eq(start.id()),any(Timestamp.class),eq(60),eq("NORMAL"))).thenReturn(1);
        assertThatCode(()->store.begin(named)).doesNotThrowAnyException();
    }
    @Test void rejectsMissingAndUnknownDifficultyBeforeDatabaseAccess() {
        var start=MatchJournalTest.start();
        for(String difficulty:Arrays.asList(null,"UNKNOWN"))
            assertThatThrownBy(()->store.begin(new MatchStart(start.id(),start.startedAt(),60,start.players(),difficulty)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsNamesWithFewerThanThreeStrippedCodePoints() {
        var start=MatchJournalTest.start();
        var named=new MatchStart(start.id(),start.startedAt(),60,List.of(
            new MatchStart.Participant(start.players().getFirst().accountId(),"  🧙🧙  "),start.players().getLast()));
        assertThatThrownBy(()->store.begin(named)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsForgedMissingAndDuplicateFinishParticipants() {
        var start=MatchJournalTest.start();
        var finish=MatchJournalTest.finish(start,"WON");
        var first=finish.players().getFirst();
        assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"WON",1200,2,10,3,List.of(first)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"WON",1200,2,10,3,List.of(first,first)))).isInstanceOf(IllegalArgumentException.class);
        var forged=new MatchFinish.PlayerResult(UUID.randomUUID(),0,0,1,false,Map.of());
        assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"WON",1200,2,10,3,List.of(first,forged)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsInvalidPhaseTicksStatsAndUpgradeBuild() {
        var start=MatchJournalTest.start();
        var players=MatchJournalTest.finish(start,"WON").players();
        assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"RUNNING",0,1,0,0,players))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"WON",36001,1,0,0,players))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"WON",1201,1,0,0,players))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"WON",1200,11,0,0,players))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"WON",1200,1,-1,0,players))).isInstanceOf(IllegalArgumentException.class);
        for(var build:List.of(Map.of("POWER",6),Map.of("UNKNOWN",1),Map.of("HASTE",-1))) {
            var bad=new MatchFinish.PlayerResult(players.getFirst().accountId(),0,0,1,false,build);
            assertThatThrownBy(()->store.finish(start,new MatchFinish(start.id(),"WON",1200,1,0,0,List.of(bad,players.getLast())))).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
