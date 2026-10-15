package com.gamedead.server.auth;
import com.gamedead.server.account.*;
import com.gamedead.server.error.AuthenticationFailedException;
import com.gamedead.protocol.auth.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Clock;
import java.util.UUID;
@Service
public class SessionService {
    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final AccountInputPolicy policy;
    private final PasswordEncoder encoder;
    private final TokenCodec codec;
    private final Clock clock;
    private final String dummyHash;
    public SessionService(AccountRepository accounts,AuthSessionRepository sessions,AccountInputPolicy policy,PasswordEncoder encoder,TokenCodec codec,Clock clock) {
        this.accounts=accounts; this.sessions=sessions; this.policy=policy; this.encoder=encoder; this.codec=codec; this.clock=clock;
        this.dummyHash=encoder.encode("unavailable-account-placeholder");
    }
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String login=policy.normalizeLogin(request.login());
        policy.validatePassword(request.password());
        Account account=accounts.findByLogin(login).orElse(null);
        boolean valid=encoder.matches(request.password(), account==null?dummyHash:account.passwordHash());
        if(account==null || !valid || !account.enabled()) throw new AuthenticationFailedException();
        var now=clock.instant(); var expires=now.plusSeconds(8*60*60);
        String token=codec.generate();
        sessions.saveAndFlush(new AuthSession(UUID.randomUUID(),account.id(),codec.hash(token),now,expires));
        account.markLoggedIn(now);
        accounts.save(account);
        return new LoginResponse(token,"Bearer",expires);
    }
    @Transactional(readOnly=true)
    public AccountPrincipal authenticate(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new AuthenticationFailedException();
        var session=sessions.findByTokenHash(codec.hash(token)).filter(value->value.isActiveAt(clock.instant()))
            .orElseThrow(AuthenticationFailedException::new);
        accounts.findById(session.accountId()).filter(Account::enabled).orElseThrow(AuthenticationFailedException::new);
        return new AccountPrincipal(session.accountId(),session.id());
    }
    @Transactional
    public void logout(UUID sessionId) {
        var session=sessions.findById(sessionId).orElseThrow(AuthenticationFailedException::new);
        session.revoke(clock.instant());
        sessions.save(session);
    }
}
