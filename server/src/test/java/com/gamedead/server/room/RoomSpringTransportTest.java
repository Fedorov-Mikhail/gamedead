package com.gamedead.server.room;

import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.protocol.room.*;
import com.gamedead.server.account.AccountService;
import com.gamedead.server.auth.*;
import com.gamedead.server.config.*;
import com.gamedead.server.error.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring Security, MVC, embedded Tomcat and WebSocket; persistence is replaced at its boundary. */
class RoomSpringTransportTest {
    private static final UUID FIRST=UUID.fromString("00000000-0000-0000-0000-000000000001"),SECOND=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void authenticatedHttpOperationsAndSocketUpdatesWorkTogetherWithoutDatabase() throws Exception {
        try(var context=(ServletWebServerApplicationContext)new SpringApplicationBuilder(TestApplication.class)
                .logStartupInfo(false).run("--server.port=0","--server.address=127.0.0.1","--spring.flyway.enabled=false","--logging.level.root=WARN")){
            String base="http://127.0.0.1:"+context.getWebServer().getPort();
            var http=HttpClient.newHttpClient();
            try{
                assertThatThrownBy(()->http.newWebSocketBuilder().buildAsync(URI.create(base.replace("http:","ws:")+"/ws/game"),new Inbox()).join())
                        .hasCauseInstanceOf(WebSocketHandshakeException.class);
                Inbox a=new Inbox(),b=new Inbox();
                WebSocket one=http.newWebSocketBuilder().header("Authorization","Bearer first").buildAsync(URI.create(base.replace("http:","ws:")+"/ws/game"),a).join();
                WebSocket two=http.newWebSocketBuilder().header("Authorization","Bearer second").buildAsync(URI.create(base.replace("http:","ws:")+"/ws/game"),b).join();
                a.waitFor(e->e.state()!=null);b.waitFor(e->e.state()!=null);
                var created=post(http,base,"first","create","",201);
                post(http,base,"second","join",json.writeValueAsString(new JoinRoomRequest(created.room().code())),200);
                post(http,base,"first","ready","{\"ready\":true}",200);post(http,base,"second","ready","{\"ready\":true}",200);
                var playing=post(http,base,"first","start","",200);
                assertThat(playing.room().status()).isEqualTo("PLAYING");
                one.sendText(json.writeValueAsString(new com.gamedead.protocol.world.InputCommand(com.gamedead.protocol.world.InputCommand.VERSION,1,1,0,false,1,0,true,false)),true).join();
                var moving=b.waitFor(e->e.world()!=null && e.world().players().getFirst().x()>80).world();
                assertThat(moving.players()).hasSize(2);
                assertThat(moving.projectiles()).isNotEmpty();
                var stopped=post(http,base,"first","stop","",200);
                assertThat(stopped.room().status()).isEqualTo("PLAYING");
                assertThat(b.waitFor(e->e.world()!=null && e.world().phase().equals("ABORTED")).world().phase()).isEqualTo("ABORTED");
                stopped=post(http,base,"first","stop","",200);
                long stoppedRevision=stopped.revision();
                assertThat(b.waitFor(e->e.state()!=null && e.state().revision()>=stoppedRevision).state().room().status()).isEqualTo("LOBBY");
                var oversized=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/rooms/join")).header("Authorization","Bearer first")
                        .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("x".repeat(9000))).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(oversized.statusCode()).isEqualTo(413);
                one.abort();two.abort();
            }finally{http.shutdownNow();}
        }
    }
    private RoomStateResponse post(HttpClient http,String base,String token,String action,String body,int status)throws Exception{
        var reply=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/rooms/"+action)).header("Authorization","Bearer "+token)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(reply.statusCode()).as("Room operation %s",action).isEqualTo(status);
        return json.readValue(reply.body(),RoomStateResponse.class);
    }
    private final class Inbox implements WebSocket.Listener {
        final BlockingQueue<RoomEvent> events=new LinkedBlockingQueue<>();final StringBuilder text=new StringBuilder();
        public void onOpen(WebSocket socket){socket.request(1);}
        public CompletionStage<?> onText(WebSocket socket,CharSequence part,boolean last){text.append(part);if(last){events.add(json.readValue(text.toString(),RoomEvent.class));text.setLength(0);}socket.request(1);return null;}
        RoomEvent waitFor(java.util.function.Predicate<RoomEvent> predicate)throws Exception{
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
            while(System.nanoTime()<end){var event=events.poll(100,TimeUnit.MILLISECONDS);if(event!=null && predicate.test(event))return event;}
            throw new AssertionError("Spring socket event not received");
        }
    }
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(excludeName="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    @Import({CoreConfiguration.class,SecurityConfiguration.class,ApiErrorWriter.class,ApiExceptionHandler.class,RoomRegistry.class,RoomController.class,GameSocketConfiguration.class})
    static class TestApplication {
        @Bean com.gamedead.server.match.MatchJournal matchJournal(java.time.Clock clock){return RoomTestJournal.create(clock);}
        @Bean SessionService sessionService(){
            var sessions=mock(SessionService.class);
            when(sessions.authenticate(anyString())).thenAnswer(call->switch((String)call.getArgument(0)){
                case "first" -> new AccountPrincipal(FIRST,FIRST);case "second" -> new AccountPrincipal(SECOND,SECOND);default -> throw new AuthenticationFailedException();});
            return sessions;
        }
        @Bean AccountService accountService(){
            var accounts=mock(AccountService.class);
            when(accounts.getProfile(any())).thenAnswer(call->new PlayerProfileResponse(call.getArgument(0),"mage","Маг Ёж",0,"mage_default"));
            return accounts;
        }
    }
}
