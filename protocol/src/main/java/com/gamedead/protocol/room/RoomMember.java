package com.gamedead.protocol.room;
import java.util.UUID;
public record RoomMember(UUID accountId, String displayName, boolean ready, boolean connected, int color) { }
