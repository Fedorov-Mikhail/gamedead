package com.gamedead.server.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="auth_sessions",schema="game")
public class AuthSession {
    @Id private UUID id;
    @Column(name="account_id",nullable=false) private UUID accountId;
    @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    protected AuthSession() {}
    public AuthSession(UUID id,UUID accountId,String tokenHash,Instant createdAt,Instant expiresAt) {
        this.id=id; this.accountId=accountId; this.tokenHash=tokenHash; this.createdAt=createdAt; this.expiresAt=expiresAt;
    }
    public UUID id(){return id;}
    public UUID accountId(){return accountId;}
    public String tokenHash(){return tokenHash;}
    public boolean isActiveAt(Instant now){return revokedAt==null && now.isBefore(expiresAt);}
    public void revoke(Instant now){if(revokedAt==null) revokedAt=now.isBefore(createdAt)?createdAt:now;}
}
