package com.gamedead.server.room;

import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.InputCommand;
import com.gamedead.server.auth.AccountPrincipal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsSci;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class GameSocketServerTest {
    @TempDir Path temporary;
    private Tomcat tomcat;
    private GameSocketServer sockets;
    private final RoomRegistry rooms=new RoomRegistry(Clock.systemUTC(),240);
    private final UUID a=UUID.randomUUID(), b=UUID.randomUUID();
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private String url;
    private final java.util.concurrent.atomic.AtomicBoolean sessionValid=new java.util.concurrent.atomic.AtomicBoolean(true);

    @BeforeEach void start() throws Exception {
        tomcat=new Tomcat();tomcat.setBaseDir(temporary.toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addContext("",temporary.toString());
        Tomcat.addServlet(context,"fallback",new HttpServlet(){@Override protected void doGet(HttpServletRequest req,HttpServletResponse res){res.setStatus(404);}});
        context.addServletMappingDecoded("/","fallback");
        context.addServletContainerInitializer((ignored,sc)->{
            var filter=sc.addFilter("test-principal",(Filter)(request,response,chain)->{
                var req=(HttpServletRequest)request;String token=req.getHeader("Authorization");
                UUID account="Bearer first".equals(token)?a:"Bearer second".equals(token)?b:null;
                if(account==null){((HttpServletResponse)response).setStatus(401);return;}
                var principal=new UsernamePasswordAuthenticationToken(new AccountPrincipal(account,account),null,List.of());
                chain.doFilter(new HttpServletRequestWrapper(req){@Override public java.security.Principal getUserPrincipal(){return principal;}},response);
            });
            filter.setAsyncSupported(true);filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST),false,"/*");
        },null);
        context.addServletContainerInitializer(new WsSci(),null);
        tomcat.start();
        sockets=new GameSocketServer(rooms,p->"Маг Ёж",p->sessionValid.get());
        sockets.register(context.getServletContext());
        url="ws://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/ws/game";
    }
    @AfterEach void stop() throws Exception {
        if(sockets!=null)sockets.destroy();
        client.shutdownNow();
        if(tomcat!=null){tomcat.stop();tomcat.destroy();}
    }
    @Test void twoActualSocketsReceiveRoomAndAuthoritativeWorldUpdates() throws Exception {
        Inbox first=new Inbox(),second=new Inbox();
        var one=connect("first",first);var two=connect("second",second);
        first.room();second.room();
        String code=rooms.create(a).room().code();rooms.join(b,code);
        assertThat(first.waitFor(e->e.state()!=null && e.state().room()!=null && e.state().room().members().size()==2).state().room().code()).isEqualTo(code);
        assertThat(second.waitFor(e->e.state()!=null && e.state().room()!=null && e.state().room().members().size()==2).state().room().members()).hasSize(2);
        rooms.ready(a,true);rooms.ready(b,true);rooms.start(a);
        one.sendText(json.writeValueAsString(new InputCommand(1,1,0,false)),true).join();
        var world=second.waitFor(e->e.world()!=null && e.world().players().getFirst().x()>80).world();
        assertThat(world.players()).hasSize(2);
        assertThat(world.roomCode()).isEqualTo(code);
        one.abort();two.abort();
    }
    @Test void invalidMovementClosesOnlyItsSocket() throws Exception {
        Inbox inbox=new Inbox();var socket=connect("first",inbox);inbox.room();
        socket.sendText(json.writeValueAsString(new InputCommand(2,1,999,0,false,0,0,false,false)),true).join();
        assertThat(inbox.closed.get(4,TimeUnit.SECONDS)).isEqualTo(1008);
    }
    @Test void handshakeWithoutAccountIsRejected() {
        assertThatThrownBy(()->client.newWebSocketBuilder().buildAsync(URI.create(url),new Inbox()).join()).hasCauseInstanceOf(WebSocketHandshakeException.class);
    }
    @Test void olderClientCannotSendUnversionedMovementIntoCombat() throws Exception {
        Inbox inbox=new Inbox();var socket=connect("first",inbox);inbox.room();
        socket.sendText("{\"sequence\":1,\"x\":1,\"y\":0,\"dash\":false}",true).join();
        assertThat(inbox.closed.get(4,TimeUnit.SECONDS)).isEqualTo(1002);
    }
    @Test void revokingEstablishedSessionClosesSocketAndClearsPresence() throws Exception {
        Inbox inbox=new Inbox();connect("first",inbox);inbox.room();
        rooms.create(a);sessionValid.set(false);
        assertThat(inbox.closed.get(8,TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(rooms.state(a).room().members().getFirst().connected()).isFalse();
    }
    private WebSocket connect(String token,Inbox inbox){return client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(2)).header("Authorization","Bearer "+token).buildAsync(URI.create(url),inbox).join();}
    private final class Inbox implements WebSocket.Listener {
        final BlockingQueue<RoomEvent> events=new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed=new CompletableFuture<>();
        final StringBuilder text=new StringBuilder();
        public void onOpen(WebSocket s){s.request(1);}
        public CompletionStage<?> onText(WebSocket s,CharSequence data,boolean last){text.append(data);if(last){events.add(json.readValue(text.toString(),RoomEvent.class));text.setLength(0);}s.request(1);return null;}
        public CompletionStage<?> onClose(WebSocket s,int code,String reason){closed.complete(code);return null;}
        RoomEvent room() throws Exception{return waitFor(e->e.type().equals("ROOM"));}
        RoomEvent waitFor(java.util.function.Predicate<RoomEvent> condition) throws Exception {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
            while(System.nanoTime()<until){RoomEvent event=events.poll(100,TimeUnit.MILLISECONDS);if(event!=null && condition.test(event))return event;}
            throw new AssertionError("Expected socket event was not delivered");
        }
    }
}
