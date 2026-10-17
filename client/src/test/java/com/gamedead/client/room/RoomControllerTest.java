package com.gamedead.client.room;

import com.gamedead.client.auth.AuthController;
import com.gamedead.client.net.*;
import com.gamedead.protocol.auth.*;
import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

class RoomControllerTest {
    private final UUID id=UUID.randomUUID();
    private final FakeApi api=new FakeApi();
    private final AuthController auth=new AuthController(api,Runnable::run,ignored->{});
    private final RoomController controller=new RoomController(auth,Runnable::run,ignored->{});
    @BeforeEach void login(){auth.login("mage","password123");}
    @AfterEach void close(){controller.close();auth.close();}
    private RoomSnapshot snapshot(long revision,String status){return new RoomSnapshot("ABC234",revision,id,status,List.of(new RoomMember(id,"Маг",false,true,0)));}
    @Test void roomButtonsWaitForConnectionAndIgnoreDoubleSubmit(){
        controller.open();
        assertThat(controller.state().busy()).isTrue();
        controller.action("create",null);
        assertThat(api.requests).isZero();
        api.connected.complete(api.channel);
        controller.action("create",null);controller.action("create",null);
        assertThat(api.requests).isEqualTo(1);
        api.reply.complete(new RoomStateResponse(1,snapshot(1,"LOBBY")));
        assertThat(controller.state().room().code()).isEqualTo("ABC234");
        assertThat(controller.state().busy()).isFalse();
    }
    @Test void olderHttpResponseCannotUndoNewerRoomEvent(){
        controller.open();api.connected.complete(api.channel);
        controller.action("ready",new ReadyRequest(true));
        api.events.accept(RoomEvent.room(new RoomStateResponse(5,snapshot(5,"PLAYING"))));
        api.reply.complete(new RoomStateResponse(4,snapshot(4,"LOBBY")));
        assertThat(controller.state().room().status()).isEqualTo("PLAYING");
    }
    @Test void staleWorldCannotReturnAfterLeaveAndReconnectUsesNewGeneration(){
        controller.open();api.connected.complete(api.channel);
        Consumer<RoomEvent> old=api.events;
        old.accept(RoomEvent.room(new RoomStateResponse(5,snapshot(5,"PLAYING"))));
        old.accept(RoomEvent.world(new WorldSnapshot("ABC234",5,UUID.randomUUID(),10,List.of(new PlayerSnapshot(id,80,192,0,false,100,0)),List.of(),List.of(),"RUNNING",4790,0)));
        assertThat(controller.state().world()).isNotNull();
        api.failure.accept(new ApiFailure(0,"Связь потеряна"));
        assertThat(controller.state().connected()).isFalse();
        assertThat(controller.state().world()).isNull();
        api.connected=new CompletableFuture<>();controller.connect();api.connected.complete(api.channel);
        api.events.accept(RoomEvent.room(new RoomStateResponse(1,null)));
        old.accept(RoomEvent.room(new RoomStateResponse(99,snapshot(99,"PLAYING"))));
        assertThat(controller.state().room()).isNull();
    }
    @Test void closingBeforeConnectionCompletesClosesLateChannel(){
        api.connected=new CompletableFuture<>(){@Override public boolean cancel(boolean ignored){return false;}};
        controller.open();controller.reset();api.connected.complete(api.channel);
        assertThat(api.channel.closed).isTrue();
        assertThat(controller.state().visible()).isFalse();
    }
    @Test void resultOnSameTickIsAcceptedButCannotBeRevertedByOlderRunningSnapshot(){
        controller.open();api.connected.complete(api.channel);
        api.events.accept(RoomEvent.room(new RoomStateResponse(5,snapshot(5,"PLAYING"))));
        UUID worldId=UUID.randomUUID();
        var players=List.of(new PlayerSnapshot(id,80,192,0,false,100,0));
        api.events.accept(RoomEvent.world(new WorldSnapshot("ABC234",5,worldId,10,players,List.of(),List.of(),"RUNNING",4790,0)));
        api.events.accept(RoomEvent.world(new WorldSnapshot("ABC234",5,worldId,10,players,List.of(),List.of(),"ABORTED",4790,0)));
        assertThat(controller.state().world().phase()).isEqualTo("ABORTED");
        api.events.accept(RoomEvent.world(new WorldSnapshot("ABC234",5,worldId,10,players,List.of(),List.of(),"RUNNING",4790,0)));
        assertThat(controller.state().world().phase()).isEqualTo("ABORTED");
    }
    @Test void errorUnlocksControlsAndBackDoesNotLeaveHiddenConnection(){
        controller.open();api.connected.complete(api.channel);controller.action("join",new JoinRoomRequest("ABC234"));
        api.reply.completeExceptionally(new ApiFailure(409,"Комната заполнена"));
        assertThat(controller.state().error()).isTrue();
        assertThat(controller.state().busy()).isFalse();
        controller.back();
        assertThat(controller.state().visible()).isFalse();
        assertThat(api.channel.closed).isTrue();
    }
    @Test void upgradeDoesNotStopMovementAndDuplicateChoicesWaitForResponse(){
        controller.open();api.connected.complete(api.channel);
        api.events.accept(RoomEvent.room(new RoomStateResponse(5,snapshot(5,"PLAYING"))));
        UUID world=UUID.randomUUID();
        api.events.accept(RoomEvent.world(progression(world,5,10,"RUNNING","RUNNING",1,7)));
        controller.chooseUpgrade(0);controller.chooseUpgrade(1);
        assertThat(api.requests).isEqualTo(1);
        assertThat(api.body).isEqualTo(new UpgradeRequest(world,7,"POWER"));
        assertThat(controller.state().busy()).isFalse();
        assertThat(controller.input(1,0,false,1,0,true,false)).isTrue();
        api.events.accept(RoomEvent.world(progression(world,6,10,"RUNNING","RUNNING",0,8)));
        api.reply.complete(new RoomStateResponse(6,snapshot(6,"PLAYING")));
        assertThat(controller.state().world().players().getFirst().pendingUpgrades()).isZero();
        controller.chooseUpgrade(0);
        assertThat(api.requests).isEqualTo(1);
    }
    @Test void sameTickNewerRevisionUpdatesUpgradesAndSavedResultButTerminalCannotRevert(){
        controller.open();api.connected.complete(api.channel);
        api.events.accept(RoomEvent.room(new RoomStateResponse(5,snapshot(5,"PLAYING"))));
        UUID world=UUID.randomUUID();
        api.events.accept(RoomEvent.world(progression(world,5,10,"RUNNING","RUNNING",1,7)));
        api.events.accept(RoomEvent.world(progression(world,6,10,"RUNNING","RUNNING",0,8)));
        assertThat(controller.state().world().roomVersion()).isEqualTo(6);
        api.events.accept(RoomEvent.world(progression(world,7,10,"WON","PENDING",0,8)));
        api.events.accept(RoomEvent.world(progression(world,8,10,"WON","SAVED",0,8)));
        assertThat(controller.state().world().saveState()).isEqualTo("SAVED");
        api.events.accept(RoomEvent.world(progression(world,9,11,"RUNNING","RUNNING",0,8)));
        assertThat(controller.state().world().phase()).isEqualTo("WON");
    }
    @Test void lateUpgradeFailureCannotOverwriteNewChoiceAndResetCancelsRequest(){
        controller.open();api.connected.complete(api.channel);
        api.events.accept(RoomEvent.room(new RoomStateResponse(5,snapshot(5,"PLAYING"))));
        UUID world=UUID.randomUUID();
        api.events.accept(RoomEvent.world(progression(world,5,10,"RUNNING","RUNNING",1,1)));
        controller.chooseUpgrade(0);
        api.events.accept(RoomEvent.world(progression(world,6,10,"RUNNING","RUNNING",1,2)));
        api.reply.completeExceptionally(new ApiFailure(409,"Old choice"));
        assertThat(controller.state().error()).isFalse();
        assertThat(controller.state().world().players().getFirst().choiceId()).isEqualTo(2);
        api.reply=new CompletableFuture<>();controller.chooseUpgrade(0);
        controller.reset();
        assertThat(api.reply).isCancelled();
        assertThat(controller.upgradePending()).isFalse();
        assertThat(controller.state().visible()).isFalse();
    }
    private WorldSnapshot progression(UUID world,long revision,long tick,String phase,String save,int pending,long choice){
        return new WorldSnapshot("ABC234",revision,world,tick,List.of(new PlayerSnapshot(id,80,192,0,false,100,0,100,pending,pending>0?choice:0,pending>0?List.of("POWER","HASTE","VITALITY"):List.of(),Map.of())),List.of(),List.of(),phase,4790,0,2,0,40,save);
    }
    private final class FakeApi implements GameApi {
        CompletableFuture<RoomChannel> connected=new CompletableFuture<>();
        CompletableFuture<RoomStateResponse> reply=new CompletableFuture<>();
        Consumer<RoomEvent> events;Consumer<ApiFailure> failure;int requests;Object body;
        final Channel channel=new Channel();
        public CompletableFuture<RegisterResponse> register(RegisterRequest r){throw new UnsupportedOperationException();}
        public CompletableFuture<LoginResponse> login(LoginRequest r){return CompletableFuture.completedFuture(new LoginResponse("secret","Bearer",Instant.parse("2099-01-01T00:00:00Z")));}
        public CompletableFuture<PlayerProfileResponse> profile(String token){return CompletableFuture.completedFuture(new PlayerProfileResponse(id,"mage","Маг",0,"default"));}
        public CompletableFuture<Void> logout(String token){return CompletableFuture.completedFuture(null);}
        public CompletableFuture<RoomChannel> connectRooms(String token,Consumer<RoomEvent> events,Consumer<ApiFailure> failure){this.events=events;this.failure=failure;return connected;}
        public CompletableFuture<RoomStateResponse> room(String token,String action,Object body){requests++;this.body=body;return reply;}
        public void close(){ }
    }
    private static final class Channel implements RoomChannel {
        boolean closed;
        public boolean input(InputCommand input){return true;}
        public void close(){closed=true;}
    }
}
