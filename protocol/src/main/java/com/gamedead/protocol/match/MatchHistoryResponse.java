package com.gamedead.protocol.match;
import java.util.List;
public record MatchHistoryResponse(List<MatchSummary> matches) {
    public MatchHistoryResponse { matches=List.copyOf(matches); }
}
