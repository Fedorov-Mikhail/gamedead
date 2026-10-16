package com.gamedead.client.room;
import com.gamedead.protocol.room.RoomSnapshot;
import com.gamedead.protocol.world.WorldSnapshot;
public record RoomState(boolean visible,boolean connected,boolean busy,RoomSnapshot room,WorldSnapshot world,String message,boolean error) { }
