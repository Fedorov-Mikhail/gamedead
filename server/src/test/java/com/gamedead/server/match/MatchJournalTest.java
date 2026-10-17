package com.gamedead.server.match;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchJournalTest {
    private final MatchStore store=mock(MatchStore.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"),ZoneOffset.UTC);
    private final MatchJournal journal=new MatchJournal(store,clock);

    @Test void rejectsGamesUntilStartupRecoverySucceeds() {
        assertThatThrownBy(()->journal.reserve(start())).isInstanceOf(IllegalStateException.class);
        doThrow(new IllegalStateException("offline")).when(store).interruptUnfinished(clock.instant());
        assertThatThrownBy(journal::recover).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->journal.reserve(start())).isInstanceOf(IllegalStateException.class);
        doNothing().when(store).interruptUnfinished(clock.instant());
        journal.recover();
        assertThat(journal.reserve(start()).state()).isEqualTo("RUNNING");
    }

    @Test void retainsFirstCompletionForRetryAndOnlyFreesCapacityAfterSave() {
        journal.recover();
        var start=start();
        var handle=journal.reserve(start);
        journal.begin(handle);
        var won=finish(start,"WON");
        journal.complete(handle,won);
        journal.complete(handle,finish(start,"LOST"));
        assertThat(handle.state()).isEqualTo("PENDING");
        doThrow(new IllegalStateException("offline")).doNothing().when(store).finish(start,won);
        journal.flush();
        assertThat(handle.state()).isEqualTo("RETRYING");
        journal.flush();
        assertThat(handle.state()).isEqualTo("SAVED");
        journal.complete(handle,finish(start,"ABORTED"));
        journal.flush();
        assertThat(handle.state()).isEqualTo("SAVED");
        verify(store,times(2)).finish(start,won);
        verify(store,never()).finish(start,finish(start,"LOST"));
    }

    @Test void reservesBoundedSlotsAndCancellationReleasesFailedStart() {
        journal.recover();
        var first=journal.reserve(start());
        for(int i=1;i<128;i++) journal.reserve(start());
        assertThatThrownBy(()->journal.reserve(start())).isInstanceOf(IllegalStateException.class);
        journal.cancel(first);
        var last=journal.reserve(start());
        journal.begin(last);
        journal.complete(last,finish(last.start(),"ABORTED"));
        journal.flush();
        assertThat(last.state()).isEqualTo("SAVED");
        assertThatCode(()->journal.reserve(start())).doesNotThrowAnyException();
    }

    @Test void slowDatabaseSaveDoesNotBlockMemoryReservationOrCompletion() throws Exception {
        journal.recover();
        var first=journal.reserve(start());
        journal.begin(first);
        journal.complete(first,finish(first.start(),"WON"));
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");return null;})
            .when(store).finish(eq(first.start()),any());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var saving=pool.submit(journal::flush);
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(()->journal.reserve(start())).get(1,TimeUnit.SECONDS);
                pool.submit(()->journal.complete(second,finish(second.start(),"ABORTED"))).get(1,TimeUnit.SECONDS);
                assertThat(second.state()).isEqualTo("PENDING");
            } finally {release.countDown();}
            saving.get(5,TimeUnit.SECONDS);
        }
        assertThat(first.state()).isEqualTo("SAVED");
    }

    static MatchStart start() {
        return new MatchStart(UUID.randomUUID(),Instant.parse("2026-10-17T11:59:00Z"),60,List.of(
            new MatchStart.Participant(UUID.randomUUID(),"First Mage"),
            new MatchStart.Participant(UUID.randomUUID(),"Second Mage")));
    }
    static MatchFinish finish(MatchStart start,String status) {
        return new MatchFinish(start.id(),status,1200,2,10,3,start.players().stream()
            .map(p->new MatchFinish.PlayerResult(p.accountId(),1,25,2,false,Map.of("POWER",1))).toList());
    }
}
