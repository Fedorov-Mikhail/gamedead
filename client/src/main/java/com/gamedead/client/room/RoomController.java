package com.gamedead.client.room;
import com.gamedead.client.auth.AuthController;
import com.gamedead.protocol.world.InputCommand;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.WorldSnapshot;
import com.gamedead.client.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class RoomController implements AutoCloseable {
    private final AuthController auth;
    private final Executor ui;
    private final Consumer<RoomState> listener;
    private RoomState state=new RoomState(false,false,false,null,null,"",false);
    private RoomChannel channel;
    private CompletableFuture<?> pending;
    private long generation,revision=-1,sequence;
    private boolean closed;
    public RoomController(AuthController auth,Executor ui,Consumer<RoomState> listener){this.auth=auth;this.ui=ui;this.listener=listener;}
    public RoomState state(){return state;}
    public void open(){if(!closed && !state.visible())connect();}
    public void connect(){
        if(closed || state.busy())return;
        reset();long operation=generation;
        publish(true,false,true,null,null,"Подключаемся к игровому серверу…",false);
        pending=auth.connectRooms(event->ui.execute(()->{if(!closed && operation==generation)event(event);}),
                failure->ui.execute(()->{if(!closed && operation==generation)disconnected(failure);}));
        pending.whenComplete((value,error)->ui.execute(()->{
            if(closed || operation!=generation){if(value instanceof RoomChannel late)late.close();return;}
            pending=null;
            if(error!=null){disconnected(failure(error));return;}
            channel=(RoomChannel)value;
            publish(true,true,false,state.room(),state.world(),"",false);
        }));
    }
    public void action(String action,Object body){
        if(closed || !state.connected() || state.busy())return;
        long operation=generation;
        publish(true,true,true,state.room(),state.world(),"Выполняем…",false);
        pending=auth.room(action,body);
        pending.whenComplete((value,error)->ui.execute(()->{
            if(closed || operation!=generation)return;
            pending=null;
            if(error!=null){ApiFailure failure=failure(error);publish(true,true,false,state.room(),state.world(),failure.getMessage(),true);return;}
            accept((RoomStateResponse)value);
            publish(true,true,false,state.room(),state.world(),"",false);
        }));
    }
    private void event(RoomEvent event){
        if(event.state()!=null)accept(event.state());
        else if(event.world()!=null){
            WorldSnapshot next=event.world();RoomSnapshot room=state.room();
            if(room==null || !room.status().equals("PLAYING") || !room.code().equals(next.roomCode()) || next.roomVersion()<room.version())return;
            WorldSnapshot current=state.world();
            if(current!=null && current.worldId().equals(next.worldId())
                    && (next.tick()<current.tick() || (next.tick()==current.tick()
                    && (next.phase().equals(current.phase()) || !current.phase().equals("RUNNING")))))return;
            publish(true,state.connected(),state.busy(),room,next,state.message(),state.error());
        }
    }
    private void accept(RoomStateResponse next){
        if(next.revision()<revision)return;
        revision=next.revision();
        RoomSnapshot room=next.room();WorldSnapshot world=state.world();
        if(room==null || !room.status().equals("PLAYING") || state.room()==null || !room.code().equals(state.room().code()))world=null;
        publish(true,state.connected(),state.busy(),room,world,state.message(),state.error());
    }
    private void disconnected(ApiFailure failure){
        generation++;
        if(channel!=null){channel.close();channel=null;}
        if(pending!=null){pending.cancel(true);pending=null;}
        publish(true,false,false,state.room(),null,failure.getMessage(),true);
    }
    public boolean input(double x,double y,boolean dash,double aimX,double aimY,boolean attack,boolean revive){
        return !closed && channel!=null && state.connected() && !state.busy() && state.world()!=null
                && state.world().phase().equals("RUNNING")
                && channel.input(new InputCommand(InputCommand.VERSION,++sequence,x,y,dash,aimX,aimY,attack,revive));
    }
    public void back(){if(state.room()==null || !state.connected()){reset();listener.accept(state);}}
    public void reset(){
        generation++;revision=-1;sequence=0;
        if(channel!=null){channel.close();channel=null;}
        if(pending!=null){pending.cancel(true);pending=null;}
        state=new RoomState(false,false,false,null,null,"",false);
    }
    private ApiFailure failure(Throwable error){
        while(error instanceof CompletionException && error.getCause()!=null)error=error.getCause();
        return error instanceof ApiFailure problem?problem:new ApiFailure(0,"Не удалось выполнить действие. Попробуй ещё раз.");
    }
    private void publish(boolean visible,boolean connected,boolean busy,RoomSnapshot room,WorldSnapshot world,String message,boolean error){
        state=new RoomState(visible,connected,busy,room,world,message,error);listener.accept(state);
    }
    @Override public void close(){closed=true;reset();}
}
