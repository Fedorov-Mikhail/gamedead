package com.gamedead.server.room;

import com.gamedead.server.auth.AccountPrincipal;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import java.util.function.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jakarta.websocket.*;
import jakarta.websocket.server.*;
import org.springframework.security.core.Authentication;
import com.gamedead.protocol.world.InputCommand;
import tools.jackson.databind.json.JsonMapper;

public final class GameSocketServer implements ApplicationListener<ServletWebServerInitializedEvent>, DisposableBean {
    private final RoomRegistry rooms;
    private final Function<AccountPrincipal,String> names;
    private final Predicate<AccountPrincipal> valid;
    private final JsonMapper json=JsonMapper.builder().build();
    private final Map<UUID,GameEndpoint> endpoints=new ConcurrentHashMap<>();
    private final ScheduledExecutorService ticker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("arena-tick").factory());
    private final ScheduledExecutorService sender=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("arena-socket").factory());
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private boolean registered;
    public GameSocketServer(RoomRegistry rooms,Function<AccountPrincipal,String> names,Predicate<AccountPrincipal> valid) {
        this.rooms=rooms;this.names=names;this.valid=valid;
    }
    public synchronized void register(ServletContext context) {
        if(registered)return;
        var container=(ServerContainer)context.getAttribute("jakarta.websocket.server.ServerContainer");
        if(container==null)throw new IllegalStateException("Embedded WebSocket container is unavailable");
        var config=ServerEndpointConfig.Builder.create(GameEndpoint.class,"/ws/game").configurator(new ServerEndpointConfig.Configurator(){
            @Override public <T>T getEndpointInstance(Class<T> type){return type.cast(new GameEndpoint());}
        }).build();
        try{container.addEndpoint(config);}catch(DeploymentException e){throw new IllegalStateException("Cannot register game socket",e);}
        registered=true;
        ticker.scheduleAtFixedRate(rooms::tick,50,50,TimeUnit.MILLISECONDS);
        sender.scheduleAtFixedRate(()->endpoints.values().forEach(GameEndpoint::pulse),0,25,TimeUnit.MILLISECONDS);
    }
    @Override public void onApplicationEvent(ServletWebServerInitializedEvent event){register(event.getApplicationContext().getServletContext());}
    @Override public void destroy() {
        ticker.shutdownNow();sender.shutdownNow();
        endpoints.values().forEach(e->e.close(1001,"Сервер остановлен"));
        workers.shutdown();
    }

    private final class GameEndpoint extends Endpoint {
        private final UUID connection=UUID.randomUUID();
        private final AtomicBoolean stopped=new AtomicBoolean(),sending=new AtomicBoolean(),checking=new AtomicBoolean();
        private Session session;
        private AccountPrincipal principal;
        private RoomMailbox mailbox;
        private long checkedAt=System.nanoTime(),sendStarted,window;
        private int messages;

        @Override public void onOpen(Session session,EndpointConfig config){
            this.session=session;
            if(!(session.getUserPrincipal() instanceof Authentication auth) || !(auth.getPrincipal() instanceof AccountPrincipal account)
                    || session.getQueryString()!=null){close(1008,"Требуется вход в аккаунт");return;}
            principal=account;
            session.setMaxTextMessageBufferSize(2048);
            session.setMaxBinaryMessageBufferSize(128);
            session.getAsyncRemote().setSendTimeout(2500);
            session.getUserProperties().put("org.apache.tomcat.websocket.READ_IDLE_TIMEOUT_MS",20_000L);
            session.getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT",2500L);
            try{
                mailbox=rooms.connect(account.accountId(),names.apply(account),connection);
                endpoints.put(connection,this);
                session.addMessageHandler(String.class,(MessageHandler.Whole<String>)this::message);
                session.addMessageHandler(java.nio.ByteBuffer.class,(MessageHandler.Whole<java.nio.ByteBuffer>)ignored->close(1008,"Ожидается JSON"));
            }catch(RuntimeException e){close(1008,"Не удалось открыть игровое соединение");}
        }
        private void message(String text){
            if(stopped.get())return;
            if(text.equals("ping"))return;
            long second=System.nanoTime()/1_000_000_000L;
            if(second!=window){window=second;messages=0;}
            if(++messages>60){close(1008,"Слишком много команд");return;}
            try{
                InputCommand input=json.readValue(text,InputCommand.class);
                if(input==null || input.sequence()<0 || !Double.isFinite(input.x()) || !Double.isFinite(input.y())
                        || Math.abs(input.x())>1 || Math.abs(input.y())>1)throw new IllegalArgumentException();
                rooms.input(principal.accountId(),connection,input);
            }catch(RuntimeException e){close(1008,"Некорректная команда движения");}
        }
        private void pulse(){
            if(stopped.get())return;
            if(!session.isOpen() || mailbox.closed()){close(1000,"Соединение заменено или закрыто");return;}
            long now=System.nanoTime();
            if(now-checkedAt>TimeUnit.SECONDS.toNanos(5) && checking.compareAndSet(false,true)){
                workers.execute(()->{
                    try{if(!valid.test(principal))close(1008,"Срок сессии истёк");}
                    catch(RuntimeException e){close(1008,"Сессия недоступна. Войди снова.");}
                    finally{checkedAt=System.nanoTime();checking.set(false);}
                });
            }
            if(sending.get()){
                if(now-sendStarted>TimeUnit.SECONDS.toNanos(3))close(1011,"Клиент не успевает принимать данные");
                return;
            }
            var event=mailbox.poll();if(event==null)return;
            if(!sending.compareAndSet(false,true))return;
            sendStarted=now;
            try{session.getAsyncRemote().sendText(json.writeValueAsString(event),result->{sending.set(false);if(!result.isOK())close(1011,"Соединение потеряно");});}
            catch(RuntimeException e){sending.set(false);close(1011,"Соединение потеряно");}
        }
        private void close(int code,String message){
            if(!stopped.compareAndSet(false,true))return;
            endpoints.remove(connection);
            if(principal!=null)rooms.disconnect(principal.accountId(),connection);
            if(session!=null)workers.execute(()->{try{session.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(code),message));}catch(Exception ignored){}});
        }
        @Override public void onClose(Session session,CloseReason reason){close(1000,"Соединение закрыто");}
        @Override public void onError(Session session,Throwable error){close(1011,"Соединение потеряно");}
    }
}
