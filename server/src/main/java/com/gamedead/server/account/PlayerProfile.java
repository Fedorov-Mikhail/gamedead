package com.gamedead.server.account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="player_profiles",schema="game")
public class PlayerProfile {
    @Id @Column(name="account_id") private UUID accountId;
    @Column(name="display_name",nullable=false,length=24) private String displayName;
    @Column(name="account_xp",nullable=false) private long accountXp;
    @Column(name="appearance_id",nullable=false,length=64) private String appearanceId;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected PlayerProfile() {}
    public PlayerProfile(UUID accountId,String displayName,Instant now) {
        this.accountId=accountId; this.displayName=displayName; this.accountXp=0; this.appearanceId="mage_default"; this.updatedAt=now;
    }
    public String displayName(){return displayName;}
    public long accountXp(){return accountXp;}
    public String appearanceId(){return appearanceId;}
}
