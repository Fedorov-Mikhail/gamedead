package com.gamedead.protocol.account;
import java.util.UUID;
public record PlayerProfileResponse(UUID accountId, String login, String displayName, long accountXp, String appearanceId) {}
