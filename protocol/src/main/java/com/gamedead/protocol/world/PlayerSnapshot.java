package com.gamedead.protocol.world;
import java.util.UUID;
public record PlayerSnapshot(UUID accountId, double x, double y, double dashCooldown, boolean moving) { }
