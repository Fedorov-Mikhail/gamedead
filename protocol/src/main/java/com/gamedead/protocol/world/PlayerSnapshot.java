package com.gamedead.protocol.world;
import java.util.*;
public record PlayerSnapshot(UUID accountId, double x, double y, double dashCooldown, boolean moving,
                             int health, double reviveProgress,int maxHealth,int pendingUpgrades,long choiceId,
                             List<String> upgradeOffers,Map<String,Integer> upgrades) {
    public PlayerSnapshot {
        upgradeOffers=List.copyOf(upgradeOffers);upgrades=Map.copyOf(upgrades);
    }
    public PlayerSnapshot(UUID accountId,double x,double y,double dashCooldown,boolean moving,int health,double reviveProgress){
        this(accountId,x,y,dashCooldown,moving,health,reviveProgress,100,0,0,List.of(),Map.of());
    }
}
