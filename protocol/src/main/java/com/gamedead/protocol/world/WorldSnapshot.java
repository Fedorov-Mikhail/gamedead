package com.gamedead.protocol.world;
import java.util.*;
public record WorldSnapshot(String roomCode, long roomVersion, UUID worldId, long tick, List<PlayerSnapshot> players,
                            List<EnemySnapshot> enemies, List<ProjectileSnapshot> projectiles,
                            String phase, int remainingTicks, int kills,int level,int experience,int requiredExperience,String saveState,
                            int wave,boolean bossSpawned) {
    public WorldSnapshot {
        players = List.copyOf(players);
        enemies = List.copyOf(enemies);
        projectiles = List.copyOf(projectiles);
    }
    public WorldSnapshot(String roomCode,long roomVersion,UUID worldId,long tick,List<PlayerSnapshot> players,
                         List<EnemySnapshot> enemies,List<ProjectileSnapshot> projectiles,String phase,int remainingTicks,int kills,
                         int level,int experience,int requiredExperience,String saveState){
        this(roomCode,roomVersion,worldId,tick,players,enemies,projectiles,phase,remainingTicks,kills,
                level,experience,requiredExperience,saveState,Math.min(30,1+(int)(tick/1200)),false);
    }
    public WorldSnapshot(String roomCode,long roomVersion,UUID worldId,long tick,List<PlayerSnapshot> players,
                         List<EnemySnapshot> enemies,List<ProjectileSnapshot> projectiles,String phase,int remainingTicks,int kills){
        this(roomCode,roomVersion,worldId,tick,players,enemies,projectiles,phase,remainingTicks,kills,
                1,0,Math.max(1,players.size())*30,phase.equals("RUNNING")?"RUNNING":"SAVED");
    }
}
