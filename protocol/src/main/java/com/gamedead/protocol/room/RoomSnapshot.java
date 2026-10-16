package com.gamedead.protocol.room;
import java.util.*;
public record RoomSnapshot(String code, long version, UUID ownerId, String status, List<RoomMember> members) {
    public RoomSnapshot { members = List.copyOf(members); }
}
