package com.gamedead.protocol.room;
import java.util.UUID;
public record UpgradeRequest(UUID worldId,long choiceId,String upgrade) {}
