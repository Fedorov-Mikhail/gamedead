package com.gamedead.client.net;
import com.gamedead.game.ArenaMap;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.*;
import java.util.*;

final class RoomMessages {
    private RoomMessages(){ }
    static boolean valid(RoomStateResponse state){
        if(state==null || state.revision()<0)return false;
        RoomSnapshot room=state.room();if(room==null)return true;
        if(room.code()==null || !room.code().matches("[A-HJ-NP-Z2-9]{6}") || room.ownerId()==null || room.version()<0 || room.version()>state.revision()
                || !("LOBBY".equals(room.status()) || "PLAYING".equals(room.status())) || room.members().isEmpty() || room.members().size()>4)return false;
        Set<UUID> ids=new HashSet<>();
        for(RoomMember m:room.members())if(m==null || m.accountId()==null || !ids.add(m.accountId()) || m.displayName()==null || m.displayName().length()>96 || m.color()<0 || m.color()>3)return false;
        return ids.contains(room.ownerId());
    }
    static boolean valid(WorldSnapshot world){
        if(world==null || world.roomCode()==null || world.worldId()==null || world.roomVersion()<0 || world.tick()<0 || world.players().isEmpty() || world.players().size()>4)return false;
        Set<UUID> ids=new HashSet<>();
        for(PlayerSnapshot p:world.players())if(p==null || p.accountId()==null || !ids.add(p.accountId()) || !Double.isFinite(p.x()) || !Double.isFinite(p.y())
                || p.x()<0 || p.x()>ArenaMap.WIDTH || p.y()<0 || p.y()>ArenaMap.HEIGHT || !Double.isFinite(p.dashCooldown()) || p.dashCooldown()<0)return false;
        return true;
    }
}
