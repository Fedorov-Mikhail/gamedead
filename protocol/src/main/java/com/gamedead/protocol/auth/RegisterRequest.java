package com.gamedead.protocol.auth;
public record RegisterRequest(String login, String displayName, String password) {
    @Override public String toString() { return "RegisterRequest[credentials redacted]"; }
}
