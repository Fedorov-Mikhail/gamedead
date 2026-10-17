package com.gamedead.server.match;
import java.time.Instant;
import java.util.*;
public record MatchStart(UUID id,Instant startedAt,int durationSeconds,List<Participant> players,String difficulty) {
    public MatchStart { players=List.copyOf(players); }
    public MatchStart(UUID id,Instant startedAt,int durationSeconds,List<Participant> players) {
        this(id,startedAt,durationSeconds,players,"NORMAL");
    }
    public record Participant(UUID accountId,String displayName) {}
}
