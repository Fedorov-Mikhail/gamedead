package com.gamedead.protocol.auth;
public record LoginRequest(String login, String password) {
    @Override public String toString() { return "LoginRequest[credentials redacted]"; }
}
