package com.gamedead.server.auth;
import java.util.UUID;
public record AccountPrincipal(UUID accountId,UUID sessionId) {}
