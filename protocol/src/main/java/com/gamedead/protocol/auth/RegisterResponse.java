package com.gamedead.protocol.auth;
import java.util.UUID;
public record RegisterResponse(UUID accountId, String login, String displayName) {}
