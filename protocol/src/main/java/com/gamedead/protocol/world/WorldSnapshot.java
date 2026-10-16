package com.gamedead.protocol.world;
import java.util.*;
public record WorldSnapshot(String roomCode, long roomVersion, UUID worldId, long tick, List<PlayerSnapshot> players,
                            List<EnemySnapshot> enemies, List<ProjectileSnapshot> projectiles,
                            String phase, int remainingTicks, int kills) {
    public WorldSnapshot {
        players = List.copyOf(players);
        enemies = List.copyOf(enemies);
        projectiles = List.copyOf(projectiles);
    }
}
