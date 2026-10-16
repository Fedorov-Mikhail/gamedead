package com.gamedead.protocol.world;
import java.util.*;
public record WorldSnapshot(String roomCode, long roomVersion, UUID worldId, long tick, List<PlayerSnapshot> players) {
    public WorldSnapshot { players = List.copyOf(players); }
}
