package com.gamedead.server.room;

import com.gamedead.server.match.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomPersistenceTest {
    private final Clock clock=Clock.systemUTC();
    private final MatchStore store=mock(MatchStore.class);
    private final MatchJournal journal=new MatchJournal(store,clock);
    private final RoomRegistry rooms=new RoomRegistry(clock,1,journal);
    private final UUID owner=UUID.randomUUID(),guest=UUID.randomUUID(),guestConnection=UUID.randomUUID();
    private RoomMailbox ownerBox;
    private void ready(){
        journal.recover();ownerBox=rooms.connect(owner,"Первый маг",UUID.randomUUID());
        String code=rooms.create(owner).room().code();rooms.connect(guest,"Второй маг",guestConnection);
        rooms.join(guest,code);rooms.ready(owner,true);rooms.ready(guest,true);
    }
    @Test void startWritesOutsideRoomLockAndRechecksPresenceBeforeActivating() throws Exception {
        ready();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return null;}).when(store).begin(any());
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var start=pool.submit(()->rooms.start(owner));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(pool.submit(()->rooms.state(owner)).get(2,TimeUnit.SECONDS).room().status()).isEqualTo("STARTING");
                pool.submit(()->rooms.disconnect(guest,guestConnection)).get(2,TimeUnit.SECONDS);
            } finally {release.countDown();}
            assertThatThrownBy(()->start.get(2,TimeUnit.SECONDS)).hasCauseInstanceOf(RoomProblem.class);
        }
        assertThat(rooms.state(owner).room().status()).isEqualTo("LOBBY");
        journal.flush();verify(store).finish(any(),argThat(f->f.status().equals("ABORTED") && f.players().size()==2));
    }
    @Test void unavailableDatabaseLeavesRoomReadyForRetryRatherThanRunning() {
        ready();doThrow(new IllegalStateException("offline")).when(store).begin(any());
        assertThatThrownBy(()->rooms.start(owner)).isInstanceOf(RoomProblem.class)
                .satisfies(e->assertThat(((RoomProblem)e).code()).isEqualTo("SAVE_UNAVAILABLE"));
        assertThat(rooms.state(owner).room().status()).isEqualTo("LOBBY");
        assertThat(rooms.state(owner).room().members()).allMatch(p->!p.ready());
        journal.flush();verify(store,never()).finish(any(),any());
    }
    @Test void deletingLastRoomDoesNotLosePendingOutcomeAndDepartedRoster() {
        ready();rooms.start(owner);rooms.leave(owner);rooms.leave(guest);
        assertThat(rooms.state(owner).room()).isNull();journal.flush();journal.flush();
        verify(store,times(1)).finish(any(),argThat(f->f.status().equals("ABORTED")
                && f.players().size()==2 && f.players().stream().allMatch(MatchFinish.PlayerResult::left)));
    }
    @Test void savedStateIsPublishedEvenWhenTerminalWorldTickDoesNotAdvance() {
        ready();rooms.start(owner);for(int i=0;i<20;i++)rooms.tick();
        var pending=latestWorld();assertThat(pending.phase()).isEqualTo("LOST");assertThat(pending.saveState()).isEqualTo("PENDING");
        journal.flush();rooms.tick();var saved=latestWorld();
        assertThat(saved.tick()).isEqualTo(pending.tick());assertThat(saved.roomVersion()).isGreaterThan(pending.roomVersion());
        assertThat(saved.saveState()).isEqualTo("SAVED");journal.flush();verify(store,times(1)).finish(any(),any());
    }
    private com.gamedead.protocol.world.WorldSnapshot latestWorld(){
        com.gamedead.protocol.world.WorldSnapshot world=null;com.gamedead.protocol.room.RoomEvent event;
        while((event=ownerBox.poll())!=null)if(event.world()!=null)world=event.world();
        return Objects.requireNonNull(world);
    }
}
