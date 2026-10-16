package com.gamedead.client.net;

import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.InputCommand;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class SocketRoomChannelTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private final List<RoomEvent> events=new ArrayList<>();
    private final List<ApiFailure> failures=new ArrayList<>();
    private final SocketRoomChannel channel=new SocketRoomChannel(json,events::add,failures::add);
    private final TestSocket socket=new TestSocket();
    @BeforeEach void open(){channel.onOpen(socket);}
    @AfterEach void close(){channel.close();}
    @Test void fragmentedInitialRoomIsReassembledBeforeOpeningControls(){
        String value=json.writeValueAsString(RoomEvent.room(new RoomStateResponse(1,null)));
        channel.onText(socket,value.substring(0,10),false);
        assertThat(channel.ready()).isNotDone();assertThat(events).isEmpty();
        channel.onText(socket,value.substring(10),true);
        assertThat(channel.ready().join()).isSameAs(channel);
        assertThat(events).hasSize(1);
    }
    @Test void oversizedMessageAbortsBeforeUnboundedBuffering(){
        channel.onText(socket,"x".repeat(65_537),false);
        assertThat(socket.aborted).isTrue();assertThat(failures).hasSize(1);assertThat(events).isEmpty();
    }
    @Test void incompatibleProtocolIsRejected(){
        channel.onText(socket,"{\"protocolVersion\":2,\"type\":\"ROOM\",\"state\":{\"revision\":1,\"room\":null}}",true);
        assertThat(socket.aborted).isTrue();assertThat(events).isEmpty();
    }
    @Test void missingRoomMemberIdentityNeverReachesUi(){
        channel.onText(socket,"{\"protocolVersion\":1,\"type\":\"ROOM\",\"state\":{\"revision\":1,\"room\":{\"code\":\"ABC234\",\"version\":1,\"ownerId\":\"00000000-0000-0000-0000-000000000001\",\"status\":\"LOBBY\",\"members\":[{\"accountId\":null,\"displayName\":\"Маг\",\"ready\":false,\"connected\":true,\"color\":0}]}}}",true);
        assertThat(socket.aborted).isTrue();assertThat(events).isEmpty();
    }
    @Test void inputBackpressureAllowsRetryOfDashWithoutBuildingQueue(){
        assertThat(channel.input(new InputCommand(1,1,0,true))).isTrue();
        assertThat(channel.input(new InputCommand(2,1,0,true))).isFalse();
        assertThat(socket.sent).hasSize(1);
        socket.inFlight.complete(socket);socket.inFlight=new CompletableFuture<>();
        assertThat(channel.input(new InputCommand(3,1,0,true))).isTrue();
        assertThat(json.readTree(socket.sent.getLast()).get("dash").asBoolean()).isTrue();
    }
    @Test void closingPendingConnectionCompletesWaiterAndAbortsSocket(){
        channel.close();assertThat(channel.ready()).isCompletedExceptionally();assertThat(socket.aborted).isTrue();
    }
    private static final class TestSocket implements WebSocket {
        boolean aborted;CompletableFuture<WebSocket> inFlight=new CompletableFuture<>();final List<String> sent=new ArrayList<>();
        public CompletableFuture<WebSocket> sendText(CharSequence text,boolean last){sent.add(text.toString());return inFlight;}
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data,boolean last){throw new UnsupportedOperationException();}
        public CompletableFuture<WebSocket> sendPing(ByteBuffer data){return CompletableFuture.completedFuture(this);}
        public CompletableFuture<WebSocket> sendPong(ByteBuffer data){return CompletableFuture.completedFuture(this);}
        public CompletableFuture<WebSocket> sendClose(int code,String reason){return CompletableFuture.completedFuture(this);}
        public void request(long n){ }
        public String getSubprotocol(){return "";}
        public boolean isOutputClosed(){return aborted;}
        public boolean isInputClosed(){return aborted;}
        public void abort(){aborted=true;}
    }
}
