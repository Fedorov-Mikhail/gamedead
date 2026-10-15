package com.gamedead.server.account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="accounts",schema="game")
public class Account {
    @Id private UUID id;
    @Column(nullable=false,length=24) private String login;
    @Column(name="password_hash",nullable=false,length=255) private String passwordHash;
    @Column(nullable=false) private boolean enabled;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="last_login_at") private Instant lastLoginAt;
    protected Account() {}
    public Account(UUID id,String login,String passwordHash,Instant createdAt) {
        this.id=id; this.login=login; this.passwordHash=passwordHash; this.createdAt=createdAt; this.enabled=true;
    }
    public UUID id(){return id;}
    public String login(){return login;}
    public String passwordHash(){return passwordHash;}
    public boolean enabled(){return enabled;}
    public Instant createdAt(){return createdAt;}
    public void markLoggedIn(Instant now){lastLoginAt=now;}
    public void disable(){enabled=false;}
}
