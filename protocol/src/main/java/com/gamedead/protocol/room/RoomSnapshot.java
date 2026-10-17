package com.gamedead.protocol.room;
import java.util.*;
public record RoomSnapshot(String code,long version,UUID ownerId,String status,List<RoomMember> members,String difficulty) {
    public RoomSnapshot { members = List.copyOf(members); }
    public RoomSnapshot(String code,long version,UUID ownerId,String status,List<RoomMember> members){this(code,version,ownerId,status,members,"NORMAL");}
}
