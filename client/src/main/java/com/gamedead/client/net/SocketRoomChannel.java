package com.gamedead.client.net;

import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.InputCommand;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bounded receive buffer and one in-flight input frame; never queues movement indefinitely. */
final class SocketRoomChannel implements RoomChannel,WebSocket.Listener {
    private final JsonMapper json;
    private final Consumer<RoomEvent> events;
    private final Consumer<ApiFailure> failure;
    private final CompletableFuture<RoomChannel> ready=new CompletableFuture<>();
    private final StringBuilder fragments=new StringBuilder();
    private final AtomicBoolean closed=new AtomicBoolean(),sending=new AtomicBoolean();
    private volatile WebSocket socket;
    private volatile Thread heartbeat;
    private Runnable onClosed=()->{};
    SocketRoomChannel(JsonMapper json,Consumer<RoomEvent> events,Consumer<ApiFailure> failure){this.json=json;this.events=events;this.failure=failure;}
    CompletableFuture<RoomChannel> ready(){return ready;}
    void onClosed(Runnable cleanup){onClosed=cleanup;}
    @Override public void onOpen(WebSocket socket){
        this.socket=socket;
        if(closed.get()){socket.abort();return;}
        socket.request(1);
        heartbeat=Thread.startVirtualThread(()->{
            try{while(!closed.get()){Thread.sleep(5000);if(!closed.get())socket.sendPing(java.nio.ByteBuffer.allocate(0)).whenComplete((ignored,error)->{if(error!=null)failed(error);});}}
            catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        });
    }
    @Override public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){
        if(closed.get())return null;
        try{
            if(data.length()>65_536-fragments.length())throw new IllegalArgumentException("Message too large");
            fragments.append(data);
            if(last){
                RoomEvent event=json.readValue(fragments.toString(),RoomEvent.class);fragments.setLength(0);
                if(event==null || event.protocolVersion()!=1 || !(event.type().equals("ROOM") && event.state()!=null || event.type().equals("WORLD") && event.world()!=null))throw new IllegalArgumentException("Invalid event");
                if(event.state()!=null && !RoomMessages.valid(event.state()))throw new IllegalArgumentException("Invalid room");
                if(event.world()!=null && !RoomMessages.valid(event.world()))throw new IllegalArgumentException("Invalid world");
                if(event.state()!=null)ready.complete(this);
                events.accept(event);
            }
            socket.request(1);
        }catch(RuntimeException e){failed(e);}
        return null;
    }
    @Override public CompletionStage<?> onPing(WebSocket socket,java.nio.ByteBuffer data){socket.request(1);return socket.sendPong(data);}
    @Override public CompletionStage<?> onPong(WebSocket socket,java.nio.ByteBuffer data){socket.request(1);return null;}
    @Override public CompletionStage<?> onClose(WebSocket socket,int code,String reason){failed(new IllegalStateException("Socket closed"));return null;}
    @Override public void onError(WebSocket socket,Throwable error){failed(error);}
    @Override public boolean input(InputCommand input){
        WebSocket connection=socket;
        if(closed.get() || connection==null || !sending.compareAndSet(false,true))return false;
        connection.sendText(json.writeValueAsString(input),true).orTimeout(3,TimeUnit.SECONDS).whenComplete((ignored,error)->{sending.set(false);if(error!=null)failed(error);});
        return true;
    }
    void failed(Throwable error){
        while(error instanceof CompletionException && error.getCause()!=null)error=error.getCause();
        boolean unauthorized=error instanceof WebSocketHandshakeException handshake && handshake.getResponse().statusCode()==401;
        var problem=new ApiFailure(unauthorized?401:0,unauthorized?"Сессия истекла. Вернись к профилю и войди в аккаунт снова.":"Игровое соединение потеряно. Нажми «Подключиться», чтобы вернуться в течение 30 секунд.");
        if(!ready.isDone())ready.completeExceptionally(problem);
        if(closed.get())return;
        close();failure.accept(problem);
    }
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        if(heartbeat!=null)heartbeat.interrupt();
        if(socket!=null)socket.abort();
        ready.completeExceptionally(new ApiFailure(0,"Игровое соединение закрыто."));
        onClosed.run();
    }
}
