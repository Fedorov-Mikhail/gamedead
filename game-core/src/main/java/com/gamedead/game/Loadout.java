package com.gamedead.game;

import java.util.*;

final class Loadout {
    private final EnumMap<Upgrade,Integer> ranks=new EnumMap<>(Upgrade.class);
    boolean add(Upgrade upgrade){
        int rank=rank(upgrade);
        if(rank>=upgrade.maxRank())return false;
        ranks.put(upgrade,rank+1);return true;
    }
    int rank(Upgrade upgrade){return ranks.getOrDefault(upgrade,0);}
    int damage(){return 20+5*rank(Upgrade.POWER);}
    int fireTicks(){return 7-rank(Upgrade.HASTE);}
    int projectiles(){return 1+rank(Upgrade.MULTISHOT);}
    int maxHealth(){return 100+25*rank(Upgrade.VITALITY);}
    double speed(){return 4+0.4*rank(Upgrade.SWIFTNESS);}
    int dashTicks(){return 40-6*rank(Upgrade.DASH);}
    Map<Upgrade,Integer> snapshot(){return Map.copyOf(ranks);}
}
