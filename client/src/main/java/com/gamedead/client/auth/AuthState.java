package com.gamedead.client.auth;

import com.gamedead.protocol.account.PlayerProfileResponse;
import java.util.Map;

public record AuthState(View view, boolean busy, String login, String message, boolean error,
                        Map<String, String> fieldErrors, PlayerProfileResponse profile) {
    public enum View { LOGIN, REGISTER, PROFILE }
    public AuthState { fieldErrors = Map.copyOf(fieldErrors); }
}
