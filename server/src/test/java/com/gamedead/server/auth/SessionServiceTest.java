package com.gamedead.server.auth;
import com.gamedead.server.account.*;
import com.gamedead.server.error.AuthenticationFailedException;
import com.gamedead.protocol.auth.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.*;
import java.security.SecureRandom;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SessionServiceTest {
    @Test void socketPrincipalValidationRejectsRevokedExpiredDisabledAndCrossAccountSessions(){
        var accounts=mock(AccountRepository.class);var sessions=mock(AuthSessionRepository.class);
        var now=Instant.parse("2026-10-16T00:00:00Z");
        var account=new Account(UUID.randomUUID(),"mage","unused",now.minusSeconds(100));
        var session=new AuthSession(UUID.randomUUID(),account.id(),"unused",now.minusSeconds(60),now.plusSeconds(60));
        when(accounts.findById(account.id())).thenReturn(Optional.of(account));when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        var service=new SessionService(accounts,sessions,new AccountInputPolicy(),new BCryptPasswordEncoder(4),new TokenCodec(new SecureRandom()),Clock.fixed(now,ZoneOffset.UTC));
        var principal=new AccountPrincipal(account.id(),session.id());
        service.validatePrincipal(principal);
        assertThatThrownBy(()->service.validatePrincipal(new AccountPrincipal(UUID.randomUUID(),session.id()))).isInstanceOf(AuthenticationFailedException.class);
        var expired=new AuthSession(UUID.randomUUID(),account.id(),"unused",now.minusSeconds(100),now.minusSeconds(1));
        when(sessions.findById(expired.id())).thenReturn(Optional.of(expired));
        assertThatThrownBy(()->service.validatePrincipal(new AccountPrincipal(account.id(),expired.id()))).isInstanceOf(AuthenticationFailedException.class);
        account.disable();assertThatThrownBy(()->service.validatePrincipal(principal)).isInstanceOf(AuthenticationFailedException.class);
        when(accounts.findById(account.id())).thenReturn(Optional.of(new Account(account.id(),"mage","unused",now)));
        session.revoke(now);assertThatThrownBy(()->service.validatePrincipal(principal)).isInstanceOf(AuthenticationFailedException.class);
    }
    @Test void loginStoresOnlyHashAndLogoutRevokesToken() {
        var accounts=mock(AccountRepository.class);
        var sessions=mock(AuthSessionRepository.class);
        var encoder=new BCryptPasswordEncoder(4);
        var codec=new TokenCodec(new SecureRandom());
        Instant now=Instant.parse("2026-10-15T12:00:00Z");
        var account=new Account(UUID.randomUUID(),"mage",encoder.encode(" Test password "),now);
        when(accounts.findByLogin("mage")).thenReturn(Optional.of(account));
        when(accounts.findById(account.id())).thenReturn(Optional.of(account));
        AuthSession[] stored=new AuthSession[1];
        when(sessions.saveAndFlush(any())).thenAnswer(inv->stored[0]=inv.getArgument(0));
        var service=new SessionService(accounts,sessions,new AccountInputPolicy(),encoder,codec,Clock.fixed(now,ZoneOffset.UTC));
        var result=service.login(new LoginRequest(" MAGE "," Test password "));
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-10-15T20:00:00Z"));
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(stored[0].tokenHash()).matches("[0-9a-f]{64}").isNotEqualTo(result.accessToken());
        assertThat(result.toString()).doesNotContain(result.accessToken());
        when(sessions.findByTokenHash(stored[0].tokenHash())).thenReturn(Optional.of(stored[0]));
        when(sessions.findById(stored[0].id())).thenReturn(Optional.of(stored[0]));
        assertThat(service.authenticate(result.accessToken()).accountId()).isEqualTo(account.id());
        service.logout(stored[0].id());
        assertThatThrownBy(()->service.authenticate(result.accessToken())).isInstanceOf(AuthenticationFailedException.class);
    }
    @Test void expiredTokensAndDisabledAccountsAreRejected() {
        var accounts=mock(AccountRepository.class); var sessions=mock(AuthSessionRepository.class);
        var encoder=new BCryptPasswordEncoder(4); var codec=new TokenCodec(new SecureRandom());
        var now=Instant.parse("2026-10-15T12:00:00Z");
        var account=new Account(UUID.randomUUID(),"mage",encoder.encode("Test password"),now.minusSeconds(36000));
        var token=codec.generate();
        var expired=new AuthSession(UUID.randomUUID(),account.id(),codec.hash(token),now.minusSeconds(36000),now.minusSeconds(1));
        when(sessions.findByTokenHash(codec.hash(token))).thenReturn(Optional.of(expired));
        when(accounts.findById(account.id())).thenReturn(Optional.of(account));
        var service=new SessionService(accounts,sessions,new AccountInputPolicy(),encoder,codec,Clock.fixed(now,ZoneOffset.UTC));
        assertThatThrownBy(()->service.authenticate(token)).isInstanceOf(AuthenticationFailedException.class);
        var valid=new AuthSession(UUID.randomUUID(),account.id(),codec.hash(token),now,now.plusSeconds(28800));
        when(sessions.findByTokenHash(codec.hash(token))).thenReturn(Optional.of(valid));
        account.disable();
        assertThatThrownBy(()->service.authenticate(token)).isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(()->service.authenticate("invalid")).isInstanceOf(AuthenticationFailedException.class);
    }
}
