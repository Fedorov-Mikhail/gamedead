package com.gamedead.server.room;
import com.gamedead.protocol.room.RoomEvent;
import java.util.concurrent.atomic.AtomicReference;

/** Latest-state slots: a slow connection consumes bounded memory and never blocks the game loop. */
public final class RoomMailbox {
    private final AtomicReference<RoomEvent> room=new AtomicReference<>(), world=new AtomicReference<>();
    private volatile boolean closed;
    void offer(RoomEvent event){if(!closed){if(event.type().equals("ROOM"))room.set(event);else world.set(event);}}
    public RoomEvent poll(){RoomEvent event=room.getAndSet(null);return event!=null?event:world.getAndSet(null);}
    public boolean closed(){return closed;}
    public void close(){closed=true;room.set(null);world.set(null);}
}
