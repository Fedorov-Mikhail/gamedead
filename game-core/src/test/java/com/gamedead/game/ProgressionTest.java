package com.gamedead.game;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProgressionTest {
    private final UUID one=UUID.fromString("00000000-0000-0000-0000-000000000001"),two=UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test void teamExperienceUsesOriginalRosterAndEveryMemberGetsOwnChoice() {
        ArenaWorld world=new ArenaWorld(List.of(one,two));
        world.gainExperience(59);
        assertThat(world.level()).isEqualTo(1);
        world.gainExperience(1);
        assertThat(world.level()).isEqualTo(2);
        assertThat(world.players()).allSatisfy(p->{
            assertThat(p.pendingUpgrades()).isEqualTo(1);
            assertThat(p.upgradeOffers()).hasSize(3).doesNotHaveDuplicates();
        });
        world.remove(two);world.gainExperience(89);
        assertThat(world.level()).isEqualTo(2);
        world.gainExperience(1);
        assertThat(world.level()).isEqualTo(3);
    }

    @Test void choosingNeverPausesTimeAndCannotReplayAnOldOffer() {
        ArenaWorld world=new ArenaWorld(List.of(one,two));world.gainExperience(60);
        var player=world.players().getFirst();var selected=player.upgradeOffers().getFirst();
        assertThat(world.chooseUpgrade(one,player.choiceId()+1,selected.name())).isFalse();
        assertThat(world.chooseUpgrade(one,player.choiceId(),"FORGED")).isFalse();
        assertThat(world.chooseUpgrade(one,player.choiceId(),selected.name())).isTrue();
        assertThat(world.chooseUpgrade(one,player.choiceId(),selected.name())).isFalse();
        assertThat(world.players().getFirst().upgrades()).containsEntry(selected,1);
        assertThat(world.players().get(1).pendingUpgrades()).isEqualTo(1);
        world.input(one,1,1,0,false);world.tick();
        assertThat(world.players().getFirst().x()).isGreaterThan(80);
        assertThat(world.tickNumber()).isEqualTo(1);
        world.resetConnection(two);
        assertThat(world.players().get(1).choiceId()).isEqualTo(1);
        assertThat(world.players().get(1).pendingUpgrades()).isEqualTo(1);
    }

    @Test void levelsAndRanksAreBoundedAndResultsRetainDepartedPlayers() {
        ArenaWorld world=new ArenaWorld(List.of(one,two));world.gainExperience(100_000);
        assertThat(world.level()).isEqualTo(10);
        while(world.players().getFirst().pendingUpgrades()>0){
            var player=world.players().getFirst();
            assertThat(world.chooseUpgrade(one,player.choiceId(),player.upgradeOffers().getFirst().name())).isTrue();
        }
        assertThat(world.players().getFirst().upgrades()).allSatisfy((upgrade,rank)->assertThat(rank).isBetween(1,upgrade.maxRank()));
        world.remove(one);world.abort();
        assertThat(world.results()).hasSize(2).anySatisfy(p->{assertThat(p.id()).isEqualTo(one);assertThat(p.left()).isTrue();});
        assertThat(world.chooseUpgrade(two,1,"POWER")).isFalse();
        assertThat(new ArenaWorld(List.of(one,two)).players()).allMatch(p->p.upgrades().isEmpty() && p.pendingUpgrades()==0);
    }

    @Test void loadoutCapsAllSixEffects() {
        Loadout loadout=new Loadout();
        for(Upgrade upgrade:Upgrade.values()){
            for(int i=0;i<upgrade.maxRank();i++)assertThat(loadout.add(upgrade)).isTrue();
            assertThat(loadout.add(upgrade)).isFalse();
        }
        assertThat(loadout.damage()).isEqualTo(45);
        assertThat(loadout.fireTicks()).isEqualTo(4);
        assertThat(loadout.projectiles()).isEqualTo(3);
        assertThat(loadout.maxHealth()).isEqualTo(200);
        assertThat(loadout.speed()).isCloseTo(5.2,within(0.00001));
        assertThat(loadout.dashTicks()).isEqualTo(22);
    }

    @Test void actualKillAwardsTeamExperienceAndPersonalStatsExactlyOnce() {
        ArenaWorld world=new ArenaWorld(List.of(one,two));world.spawnEnemy(112,192);
        for(int i=0;i<30;i++){world.input(one,i+1,0,0,false,1,0,true,false);world.tick();}
        assertThat(world.totalExperience()).isEqualTo(10);
        assertThat(world.results().getFirst().kills()).isEqualTo(1);
        assertThat(world.results().getFirst().damage()).isEqualTo(60);
        assertThat(world.results().get(1).damage()).isZero();
    }
    @Test void vitalityDoesNotRaiseDownedPlayerAndReviveUsesNewMaximum() {
        ArenaWorld world=new ArenaWorld(List.of(one,two));world.gainExperience(100_000);
        choose(world,Upgrade.VITALITY,1);
        assertThat(world.players().getFirst().health()).isEqualTo(125);
        for(int i=0;i<168;i++){
            world.input(two,i+1,i<24?0:i<144?-1:0,i<24?-1:i<144?0:1,false);world.tick();
        }
        world.damage(one,125);choose(world,Upgrade.VITALITY,2);
        assertThat(world.players().getFirst().health()).isZero();
        assertThat(world.players().getFirst().maxHealth()).isEqualTo(150);
        for(int i=0;i<60;i++){world.input(two,169+i,0,0,false,0,0,false,true);world.tick();}
        assertThat(world.players().getFirst().health()).isEqualTo(60);
    }
    @Test void multishotActuallyFiresDivergingProjectilesAndOverkillDoesNotInflateDamage() {
        ArenaWorld world=new ArenaWorld(List.of(one));world.gainExperience(100_000);choose(world,Upgrade.MULTISHOT,1);
        world.input(one,1,0,0,false,1,0,true,false);world.tick();
        int count=1+world.players().getFirst().upgrades().get(Upgrade.MULTISHOT);
        assertThat(world.projectiles()).hasSize(count);
        assertThat(world.projectiles()).extracting(ArenaWorld.Projectile::y).doesNotHaveDuplicates();
        world.spawnEnemy(112,192);
        for(int i=0;i<30;i++){world.input(one,i+2,0,0,false,1,0,true,false);world.tick();}
        assertThat(world.kills()).isEqualTo(1);
        assertThat(world.results().getFirst().damage()).isEqualTo(60);
    }
    private void choose(ArenaWorld world,Upgrade desired,int rank){
        while(world.players().getFirst().upgrades().getOrDefault(desired,0)<rank){
            var player=world.players().getFirst();assertThat(player.pendingUpgrades()).isPositive();
            Upgrade selected=player.upgradeOffers().contains(desired)?desired:player.upgradeOffers().getFirst();
            assertThat(world.chooseUpgrade(one,player.choiceId(),selected.name())).isTrue();
        }
    }
}
