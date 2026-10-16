package com.gamedead.protocol.room;
import com.gamedead.protocol.world.WorldSnapshot;
public record RoomEvent(int protocolVersion, String type, RoomStateResponse state, WorldSnapshot world, String message) {
    public static RoomEvent room(RoomStateResponse state) { return new RoomEvent(com.gamedead.protocol.world.InputCommand.VERSION, "ROOM", state, null, ""); }
    public static RoomEvent world(WorldSnapshot world) { return new RoomEvent(com.gamedead.protocol.world.InputCommand.VERSION, "WORLD", null, world, ""); }
}
