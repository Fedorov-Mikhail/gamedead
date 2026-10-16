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
        if(world==null || world.roomCode()==null || !world.roomCode().matches("[A-HJ-NP-Z2-9]{6}") || world.worldId()==null
                || world.roomVersion()<0 || world.tick()<0 || world.tick()>36_000 || world.players().isEmpty() || world.players().size()>4
                || world.enemies().size()>40 || world.projectiles().size()>128 || world.remainingTicks()<0 || world.remainingTicks()>36_000
                || world.kills()<0 || world.phase()==null || !Set.of("RUNNING","WON","LOST","ABORTED").contains(world.phase()))return false;
        Set<UUID> ids=new HashSet<>();
        for(PlayerSnapshot p:world.players())if(p==null || p.accountId()==null || !ids.add(p.accountId()) || !Double.isFinite(p.x()) || !Double.isFinite(p.y())
                || !position(p.x(),p.y()) || !Double.isFinite(p.dashCooldown()) || p.dashCooldown()<0 || p.dashCooldown()>2
                || p.health()<0 || p.health()>100 || !Double.isFinite(p.reviveProgress()) || p.reviveProgress()<0 || p.reviveProgress()>1)return false;
        Set<Long> entities=new HashSet<>();
        for(EnemySnapshot e:world.enemies())if(e==null || e.id()<1 || !entities.add(e.id()) || !position(e.x(),e.y()) || e.health()<1 || e.health()>60)return false;
        for(ProjectileSnapshot p:world.projectiles())if(p==null || p.id()<1 || !entities.add(p.id()) || !position(p.x(),p.y()))return false;
        return true;
    }
    private static boolean position(double x,double y){return Double.isFinite(x) && Double.isFinite(y) && x>=0 && x<=ArenaMap.WIDTH && y>=0 && y<=ArenaMap.HEIGHT;}
}
