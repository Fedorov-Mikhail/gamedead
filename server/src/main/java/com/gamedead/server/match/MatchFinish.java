package com.gamedead.server.match;
import java.util.*;
public record MatchFinish(UUID id,String status,long elapsedTicks,int teamLevel,int teamExperience,int teamKills,List<PlayerResult> players) {
    public MatchFinish { players=List.copyOf(players); }
    public record PlayerResult(UUID accountId,int kills,int damage,int level,boolean left,Map<String,Integer> upgrades) {
        public PlayerResult { upgrades=Map.copyOf(upgrades); }
    }
}
