package com.gamedead.client.net;
import com.gamedead.game.ArenaMap;
import com.gamedead.game.Upgrade;
import com.gamedead.protocol.match.*;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.*;
import java.util.*;

final class RoomMessages {
    private RoomMessages(){ }
    static boolean valid(RoomStateResponse state){
        if(state==null || state.revision()<0)return false;
        RoomSnapshot room=state.room();if(room==null)return true;
        if(room.code()==null || !room.code().matches("[A-HJ-NP-Z2-9]{6}") || room.ownerId()==null || room.version()<0 || room.version()>state.revision()
                || room.status()==null || !Set.of("LOBBY","STARTING","PLAYING").contains(room.status()) || !difficulty(room.difficulty()) || room.members().isEmpty() || room.members().size()>4)return false;
        Set<UUID> ids=new HashSet<>();
        for(RoomMember m:room.members())if(m==null || m.accountId()==null || !ids.add(m.accountId()) || m.displayName()==null || m.displayName().length()>96 || m.color()<0 || m.color()>3)return false;
        return ids.contains(room.ownerId());
    }
    static boolean valid(WorldSnapshot world){
        if(world==null || world.roomCode()==null || !world.roomCode().matches("[A-HJ-NP-Z2-9]{6}") || world.worldId()==null
                || world.roomVersion()<0 || world.tick()<0 || world.tick()>36_000 || world.players().isEmpty() || world.players().size()>4
                || world.enemies().size()>40 || world.projectiles().size()>128 || world.remainingTicks()<0 || world.remainingTicks()>36_000
                || world.kills()<0 || world.wave()<1 || world.wave()>30 || world.phase()==null || !Set.of("RUNNING","WON","LOST","ABORTED").contains(world.phase())
                || world.level()<1 || world.level()>10 || world.experience()<0
                || (world.level()==10 ? world.experience()!=0 || world.requiredExperience()!=0
                    : world.requiredExperience()<1 || world.requiredExperience()>600 || world.experience()>=world.requiredExperience())
                || world.saveState()==null || !Set.of("RUNNING","PENDING","RETRYING","SAVED").contains(world.saveState())
                || world.phase().equals("RUNNING")!=world.saveState().equals("RUNNING"))return false;
        Set<UUID> ids=new HashSet<>();
        for(PlayerSnapshot p:world.players())if(p==null || p.accountId()==null || !ids.add(p.accountId()) || !Double.isFinite(p.x()) || !Double.isFinite(p.y())
                || !position(p.x(),p.y()) || !Double.isFinite(p.dashCooldown()) || p.dashCooldown()<0 || p.dashCooldown()>2
                || p.maxHealth()<100 || p.maxHealth()>200 || p.health()<0 || p.health()>p.maxHealth()
                || !Double.isFinite(p.reviveProgress()) || p.reviveProgress()<0 || p.reviveProgress()>1
                || !ranks(p.upgrades()) || !offers(p,world.level()))return false;
        Set<Long> entities=new HashSet<>();
        int bosses=0;
        for(EnemySnapshot e:world.enemies()){
            if(e==null || e.id()<1 || !entities.add(e.id()) || !position(e.x(),e.y()) || e.health()<1 || e.health()>e.maxHealth()
                    || e.maxHealth()<1 || e.maxHealth()>100_000 || e.kind()==null || !Set.of("ZOMBIE","RUNNER","SHAMAN","BOSS").contains(e.kind())
                    || e.action()==null || !Set.of("MOVE","WINDUP","ATTACK","CHARGE","RECOVER").contains(e.action())
                    || e.actionTicks()<0 || e.actionTicks()>2000 || !axis(e.aimX()) || !axis(e.aimY())
                    || !Double.isFinite(e.radius()) || e.radius()<1 || e.radius()>24)return false;
            if(e.kind().equals("BOSS"))bosses++;
        }
        if(bosses>1 || bosses>0 && !world.bossSpawned())return false;
        for(ProjectileSnapshot p:world.projectiles())if(p==null || p.id()<1 || !entities.add(p.id()) || !position(p.x(),p.y())
                || !Double.isFinite(p.velocityX()) || !Double.isFinite(p.velocityY()) || Math.abs(p.velocityX())>1000 || Math.abs(p.velocityY())>1000)return false;
        return true;
    }
    static boolean valid(MatchHistoryResponse history){
        if(history==null || history.matches().size()>20)return false;
        Set<UUID> ids=new HashSet<>();
        for(MatchSummary m:history.matches())if(m==null || m.matchId()==null || !ids.add(m.matchId())
                || m.status()==null || !Set.of("WON","LOST","ABORTED","INTERRUPTED").contains(m.status())
                || m.startedAt()==null || m.finishedAt()==null || m.finishedAt().isBefore(m.startedAt())
                || m.durationSeconds()<0 || m.durationSeconds()>1800 || m.level()<1 || m.level()>10
                || m.teamKills()<0 || m.kills()<0 || m.kills()>m.teamKills() || m.damage()<0 || m.accountXp()<0 || !ranks(m.upgrades()) || !difficulty(m.difficulty()))return false;
        return true;
    }
    private static boolean difficulty(String value){return value!=null && Set.of("EASY","NORMAL","HARD").contains(value);}
    private static boolean axis(double value){return Double.isFinite(value) && Math.abs(value)<=1;}
    private static boolean ranks(Map<String,Integer> ranks){
        if(ranks==null || ranks.size()>Upgrade.values().length)return false;
        for(var rank:ranks.entrySet()){
            try{if(rank.getValue()==null || rank.getValue()<1 || rank.getValue()>Upgrade.valueOf(rank.getKey()).maxRank())return false;}
            catch(IllegalArgumentException | NullPointerException ignored){return false;}
        }
        return true;
    }
    private static boolean offers(PlayerSnapshot p,int level){
        int chosen=p.upgrades().values().stream().mapToInt(Integer::intValue).sum();
        if(p.pendingUpgrades()<0 || p.pendingUpgrades()>level-1 || p.choiceId()<0 || p.choiceId()>9
                || chosen+p.pendingUpgrades()!=level-1 || p.upgradeOffers().size()>3
                || new HashSet<>(p.upgradeOffers()).size()!=p.upgradeOffers().size())return false;
        if(p.pendingUpgrades()==0)return p.choiceId()==0 && p.upgradeOffers().isEmpty();
        if(p.choiceId()!=chosen+1 || p.upgradeOffers().isEmpty())return false;
        for(String offer:p.upgradeOffers()){
            try{if(p.upgrades().getOrDefault(offer,0)>=Upgrade.valueOf(offer).maxRank())return false;}
            catch(IllegalArgumentException | NullPointerException ignored){return false;}
        }
        return true;
    }
    private static boolean position(double x,double y){return Double.isFinite(x) && Double.isFinite(y) && x>=0 && x<=ArenaMap.WIDTH && y>=0 && y<=ArenaMap.HEIGHT;}
}
