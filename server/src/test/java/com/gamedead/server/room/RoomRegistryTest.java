package com.gamedead.server.room;

import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.InputCommand;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class RoomRegistryTest {
    private final MutableClock clock=new MutableClock();
    private final RoomRegistry rooms=new RoomRegistry(clock);
    private final UUID owner=UUID.randomUUID(), guest=UUID.randomUUID();
    private final Map<UUID,UUID> connections=new HashMap<>();
    private RoomMailbox connect(UUID account){UUID id=UUID.randomUUID();connections.put(account,id);return rooms.connect(account,"Игрок [gold]",id);}
    private String create(){connect(owner);return rooms.create(owner).room().code();}
    private void twoReady(){String code=create();connect(guest);rooms.join(guest,code);rooms.ready(owner,true);rooms.ready(guest,true);}

    @Test void creationIsPrivateAndUpdatesAreDeliveredToBothMembers(){
        RoomMailbox a=connect(owner), b=connect(guest);
        var created=rooms.create(owner);
        var joined=rooms.join(guest,created.room().code().toLowerCase(Locale.ROOT));
        assertThat(joined.room().members()).hasSize(2);
        assertThat(a.poll().state().room().members()).hasSize(2);
        assertThat(b.poll().state().room().code()).isEqualTo(created.room().code());
        assertThat(joined.revision()).isGreaterThan(created.revision());
    }
    @Test void concurrentJoinsNeverAdmitFifthPlayer() throws Exception {
        String code=create();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var tasks=new ArrayList<Future<Boolean>>();
            for(int i=0;i<10;i++){UUID id=UUID.randomUUID();connect(id);tasks.add(pool.submit(()->{
                try{rooms.join(id,code);return true;}catch(RoomProblem e){assertThat(e.code()).isEqualTo("ROOM_FULL");return false;}
            }));}
            int admitted=0;for(var task:tasks)if(task.get())admitted++;
            assertThat(admitted).isEqualTo(3);
        }
        assertThat(rooms.state(owner).room().members()).hasSize(4);
    }
    @Test void accountCannotCreateOrJoinTwoRooms(){
        String code=create();
        assertThatThrownBy(()->rooms.create(owner)).isInstanceOf(RoomProblem.class);
        assertThatThrownBy(()->rooms.join(owner,code)).isInstanceOf(RoomProblem.class);
    }
    @Test void startRequiresOwnerTwoConnectedReadyPlayersAndCannotRepeat(){
        String code=create();
        assertThatThrownBy(()->rooms.start(owner)).isInstanceOf(RoomProblem.class);
        connect(guest);rooms.join(guest,code);
        assertThatThrownBy(()->rooms.start(guest)).isInstanceOf(RoomProblem.class);
        rooms.ready(owner,true);
        assertThatThrownBy(()->rooms.start(owner)).isInstanceOf(RoomProblem.class);
        rooms.ready(guest,true);
        assertThat(rooms.start(owner).room().status()).isEqualTo("PLAYING");
        assertThatThrownBy(()->rooms.start(owner)).isInstanceOf(RoomProblem.class);
        UUID late=UUID.randomUUID();connect(late);
        assertThatThrownBy(()->rooms.join(late,code)).isInstanceOf(RoomProblem.class);
    }
    @Test void ownerTransferAndLastLeaveRemoveRoom(){
        String code=create();connect(guest);rooms.join(guest,code);
        rooms.leave(owner);
        assertThat(rooms.state(guest).room().ownerId()).isEqualTo(guest);
        rooms.leave(guest);
        assertThat(rooms.state(guest).room()).isNull();
        assertThatThrownBy(()->rooms.join(owner,code)).isInstanceOf(RoomProblem.class);
    }
    @Test void disconnectClearsReadyAndExpiryRemovesMember(){
        twoReady();rooms.disconnect(guest,connections.get(guest));
        var absent=rooms.state(owner).room().members().stream().filter(m->m.accountId().equals(guest)).findFirst().orElseThrow();
        assertThat(absent.ready()).isFalse();assertThat(absent.connected()).isFalse();
        clock.now=clock.now.plusSeconds(31);rooms.tick();
        assertThat(rooms.state(owner).room().members()).hasSize(1);
        assertThat(rooms.state(guest).room()).isNull();
    }
    @Test void reconnectKeepsCharacterAndAllowsNewInputSequenceButOldConnectionCannotControl(){
        twoReady();rooms.start(owner);
        UUID old=connections.get(owner);
        rooms.input(owner,old,new InputCommand(100,1,0,false));rooms.tick();
        rooms.disconnect(owner,old);
        RoomMailbox box=connect(owner);
        rooms.input(owner,old,new InputCommand(101,-1,0,false));
        rooms.input(owner,connections.get(owner),new InputCommand(1,1,0,false));
        rooms.tick();rooms.tick();
        RoomEvent event;com.gamedead.protocol.world.WorldSnapshot world=null;
        while((event=box.poll())!=null)if(event.world()!=null)world=event.world();
        assertThat(world).isNotNull();
        assertThat(world.players().getFirst().x()).isGreaterThan(84);
        assertThat(rooms.state(owner).room().members()).hasSize(2);
    }
    @Test void stoppingClearsReadinessAndOtherRoomsAreIsolated(){
        twoReady();RoomMailbox box=rooms.connect(owner,"Маг",UUID.randomUUID());
        rooms.start(owner);rooms.tick();rooms.tick();
        assertThat(box.poll().state().room().status()).isEqualTo("PLAYING");
        assertThat(box.poll().world().players()).hasSize(2);
        rooms.stop(owner);
        assertThat(rooms.state(owner).room().members()).allMatch(m->!m.ready());
        UUID other=UUID.randomUUID();connect(other);rooms.create(other);
        assertThat(rooms.state(other).room().members()).hasSize(1);
        assertThat(rooms.state(owner).room().members()).hasSize(2);
    }
    @Test void badCodesAndUnconnectedUsersAreRejected(){
        assertThatThrownBy(()->rooms.create(owner)).isInstanceOf(RoomProblem.class);
        connect(owner);
        assertThatThrownBy(()->rooms.join(owner,"bad/value")).isInstanceOf(RoomProblem.class);
    }
    @Test void slowClientReceivesLatestRoomStateInsteadOfQueuedHistory(){
        RoomMailbox box=connect(owner);rooms.create(owner);
        for(int i=0;i<1000;i++)rooms.ready(owner,i%2==0);
        assertThat(box.poll().state().revision()).isEqualTo(rooms.state(owner).revision());
        assertThat(box.poll()).isNull();
    }
    private static final class MutableClock extends Clock {
        Instant now=Instant.parse("2026-10-16T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId ignored){return this;}
        public Instant instant(){return now;}
    }
}
