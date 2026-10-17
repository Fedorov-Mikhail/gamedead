package com.gamedead.client.auth;

import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.protocol.match.MatchSummary;
import java.util.List;
import java.util.Map;

public record AuthState(View view, boolean busy, String login, String message, boolean error,
                        Map<String, String> fieldErrors, PlayerProfileResponse profile,List<MatchSummary> matches) {
    public enum View { LOGIN, REGISTER, PROFILE, HISTORY }
    public AuthState { fieldErrors = Map.copyOf(fieldErrors);matches=List.copyOf(matches); }
    public AuthState(View view,boolean busy,String login,String message,boolean error,Map<String,String> fields,PlayerProfileResponse profile){
        this(view,busy,login,message,error,fields,profile,List.of());
    }
}
