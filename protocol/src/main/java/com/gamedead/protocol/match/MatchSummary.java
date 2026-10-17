package com.gamedead.protocol.match;
import java.time.Instant;
import java.util.*;
public record MatchSummary(UUID matchId,String status,Instant startedAt,Instant finishedAt,int durationSeconds,int level,int teamKills,int kills,int damage,long accountXp,Map<String,Integer> upgrades,String difficulty) {
    public MatchSummary { upgrades=Map.copyOf(upgrades); }
    public MatchSummary(UUID matchId,String status,Instant startedAt,Instant finishedAt,int durationSeconds,int level,int teamKills,int kills,int damage,long accountXp,Map<String,Integer> upgrades) {
        this(matchId,status,startedAt,finishedAt,durationSeconds,level,teamKills,kills,damage,accountXp,upgrades,"NORMAL");
    }
}
