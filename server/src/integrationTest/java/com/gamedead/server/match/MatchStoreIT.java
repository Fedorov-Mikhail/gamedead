package com.gamedead.server.match;

import com.gamedead.server.support.IntegrationSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.*;

@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class MatchStoreIT extends IntegrationSupport {
    @Autowired MatchStore store;
    @Autowired JdbcTemplate jdbc;

    @Test void concurrentFinishAwardsExactlyOnceAndHistoryContainsOnlyOwnResults() throws Exception {
        var start=start();
        var finish=finish(start,"WON",false);
        store.begin(start);
        store.begin(start);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{gate.await();store.finish(start,finish);return true;});
            var two=pool.submit(()->{gate.await();store.finish(start,finish);return true;});
            gate.countDown();
            assertThat(one.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(two.get(20,TimeUnit.SECONDS)).isTrue();
        }
        for(var player:start.players()) assertThat(xp(player.accountId())).isEqualTo(115L);
        var own=store.history(start.players().getFirst().accountId()).matches();
        assertThat(own).hasSize(1);
        assertThat(own.getFirst().status()).isEqualTo("WON");
        assertThat(own.getFirst().durationSeconds()).isEqualTo(60);
        assertThat(own.getFirst().kills()).isEqualTo(1);
        assertThat(own.getFirst().damage()).isEqualTo(25);
        assertThat(own.getFirst().accountXp()).isEqualTo(115);
        assertThat(own.getFirst().upgrades()).containsEntry("POWER",1);
        var teammate=store.history(start.players().getLast().accountId()).matches().getFirst();
        assertThat(teammate.kills()).isEqualTo(2);
        assertThat(teammate.damage()).isEqualTo(40);
        assertThat(teammate.upgrades()).containsEntry("HASTE",1).doesNotContainKey("POWER");
        assertThat(store.history(account()).matches()).isEmpty();
    }

    @Test void missingProfileRollsBackTerminalTransitionAndAllRewards() {
        var start=start();
        store.begin(start);
        jdbc.update("DELETE FROM game.player_profiles WHERE account_id=?",start.players().getLast().accountId());
        assertThatThrownBy(()->store.finish(start,finish(start,"WON",false))).isInstanceOf(IllegalStateException.class);
        assertThat(status(start.id())).isEqualTo("RUNNING");
        assertThat(xp(start.players().getFirst().accountId())).isZero();
        assertThat(jdbc.queryForObject("SELECT sum(account_xp) FROM game.match_players WHERE match_id=?",Long.class,start.id())).isZero();
    }

    @Test void leftAndAbortedPlayersReceiveNoRewardAndStartupInterruptsUnfinished() {
        var won=start(); store.begin(won); store.finish(won,finish(won,"WON",true));
        assertThat(xp(won.players().getFirst().accountId())).isZero();
        assertThat(xp(won.players().getLast().accountId())).isEqualTo(115L);
        var aborted=start(); store.begin(aborted); store.finish(aborted,finish(aborted,"ABORTED",false));
        assertThat(xp(aborted.players().getFirst().accountId())).isZero();
        var running=start(); store.begin(running);
        assertThat(store.history(running.players().getFirst().accountId()).matches()).isEmpty();
        store.interruptUnfinished(running.startedAt().minusSeconds(1));
        assertThat(status(running.id())).isEqualTo("INTERRUPTED");
        assertThat(xp(running.players().getFirst().accountId())).isZero();
        var interrupted=store.history(running.players().getFirst().accountId()).matches().getFirst();
        assertThat(interrupted.finishedAt()).isEqualTo(running.startedAt());
        store.finish(running,finish(running,"WON",false));
        assertThat(xp(running.players().getFirst().accountId())).isZero();
    }

    @Test void missingParticipantRollsBackAndDuplicateMatchIdentityCannotRewriteRoster() {
        var start=start(); store.begin(start);
        jdbc.update("DELETE FROM game.match_players WHERE match_id=? AND account_id=?",start.id(),start.players().getLast().accountId());
        assertThatThrownBy(()->store.finish(start,finish(start,"WON",false))).isInstanceOf(IllegalStateException.class);
        assertThat(status(start.id())).isEqualTo("RUNNING");
        assertThat(xp(start.players().getFirst().accountId())).isZero();
        var other=new MatchStart(start.id(),start.startedAt(),60,List.of(start.players().getFirst(),new MatchStart.Participant(account(),"Other Mage")));
        assertThatThrownBy(()->store.begin(other)).isInstanceOf(IllegalStateException.class);
    }

    @Test void persistsDifficultyInHistoryAndRejectsChangingItForTheSameMatch() {
        var original=start();
        var hard=new MatchStart(original.id(),original.startedAt(),original.durationSeconds(),original.players(),"HARD");
        store.begin(hard);
        var normal=new MatchStart(hard.id(),hard.startedAt(),hard.durationSeconds(),hard.players(),"NORMAL");
        assertThatThrownBy(()->store.begin(normal)).isInstanceOf(IllegalStateException.class);
        store.finish(hard,finish(hard,"WON",false));
        assertThat(store.history(hard.players().getFirst().accountId()).matches().getFirst().difficulty()).isEqualTo("HARD");
    }

    @Test void historyIsLastTwentyOwnFinishedMatches() {
        UUID account=account();
        for(int i=0;i<22;i++) {
            var start=new MatchStart(UUID.randomUUID(),Instant.now().minusSeconds(100+i),60,List.of(
                new MatchStart.Participant(account,"History Mage"),new MatchStart.Participant(account(),"Other Mage")));
            store.begin(start);store.finish(start,finish(start,"LOST",false));
        }
        var history=store.history(account).matches();
        assertThat(history).hasSize(20);
        assertThat(history).allSatisfy(m->{assertThat(m.status()).isEqualTo("LOST");assertThat(m.accountXp()).isEqualTo(15);});
        assertThat(history).extracting(m->m.finishedAt()).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test void failedParticipantInsertRollsBackEntireBegin() {
        var start=new MatchStart(UUID.randomUUID(),Instant.now(),60,List.of(
            new MatchStart.Participant(account(),"Existing Mage"),new MatchStart.Participant(UUID.randomUUID(),"Unknown Mage")));
        assertThatThrownBy(()->store.begin(start)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game.matches WHERE id=?",Integer.class,start.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game.match_players WHERE match_id=?",Integer.class,start.id())).isZero();
    }

    @Test void historyRouteUsesAuthenticatedIdentityAndRejectsAnonymousRequests() throws Exception {
        String login="mh_"+UUID.randomUUID().toString().replace("-","").substring(0,8);
        var registered=register(login);
        assertThat(registered.statusCode()).isEqualTo(201);
        UUID own=UUID.fromString(field(registered.body(),"accountId"));
        String token=field(login(login).body(),"accessToken");
        var foreign=start();store.begin(foreign);store.finish(foreign,finish(foreign,"WON",false));
        assertThat(request("GET","/api/v1/matches/me",null,null).statusCode()).isEqualTo(401);
        var response=request("GET","/api/v1/matches/me?accountId="+foreign.players().getFirst().accountId(),null,token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).path("matches").size()).isZero();
        var ownStart=new MatchStart(UUID.randomUUID(),Instant.now().minusSeconds(60),60,List.of(
            new MatchStart.Participant(own,"Own Mage"),new MatchStart.Participant(account(),"Partner Mage")));
        store.begin(ownStart);store.finish(ownStart,finish(ownStart,"LOST",false));
        response=request("GET","/api/v1/matches/me",null,token);
        assertThat(JSON.readTree(response.body()).path("matches").size()).isEqualTo(1);
        assertThat(response.body()).contains(ownStart.id().toString()).doesNotContain(foreign.id().toString(),token,"password_hash","accountId");
    }

    private MatchStart start() {
        return new MatchStart(UUID.randomUUID(),Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS),60,List.of(
            new MatchStart.Participant(account(),"First Mage"),new MatchStart.Participant(account(),"Second Mage")));
    }
    private MatchFinish finish(MatchStart start,String status,boolean firstLeft) {
        return new MatchFinish(start.id(),status,1200,2,10,3,List.of(
            new MatchFinish.PlayerResult(start.players().getFirst().accountId(),1,25,2,firstLeft,Map.of("POWER",1)),
            new MatchFinish.PlayerResult(start.players().getLast().accountId(),2,40,2,false,Map.of("HASTE",1))));
    }
    private UUID account() {
        var id=UUID.randomUUID();
        jdbc.update("INSERT INTO game.accounts(id,login,password_hash,created_at) VALUES (?,?,?,?)",id,"m_"+id.toString().replace("-","").substring(0,12),"test-only",Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO game.player_profiles(account_id,display_name,updated_at) VALUES (?, 'Test Mage', ?)",id,Timestamp.from(Instant.now()));
        return id;
    }
    private long xp(UUID account) {return jdbc.queryForObject("SELECT account_xp FROM game.player_profiles WHERE account_id=?",Long.class,account);}
    private String status(UUID match) {return jdbc.queryForObject("SELECT status FROM game.matches WHERE id=?",String.class,match);}
}
