package com.gamedead.server.account;
import com.gamedead.protocol.auth.*;
import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.server.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.hibernate.exception.ConstraintViolationException;
import java.time.Clock;
import java.util.UUID;
@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PlayerProfileRepository profiles;
    private final AccountInputPolicy policy;
    private final PasswordEncoder encoder;
    private final Clock clock;
    public AccountService(AccountRepository accounts,PlayerProfileRepository profiles,AccountInputPolicy policy,PasswordEncoder encoder,Clock clock) {
        this.accounts=accounts; this.profiles=profiles; this.policy=policy; this.encoder=encoder; this.clock=clock;
    }
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String login=policy.normalizeLogin(request.login());
        String name=policy.normalizeDisplayName(request.displayName());
        policy.validatePassword(request.password());
        UUID id=UUID.randomUUID();
        var now=clock.instant();
        try { accounts.saveAndFlush(new Account(id,login,encoder.encode(request.password()),now)); }
        catch(DataIntegrityViolationException exception) {
            for(Throwable cause=exception;cause!=null;cause=cause.getCause()) {
                if(cause instanceof ConstraintViolationException violation && "uk_accounts_login".equals(violation.getConstraintName()))
                    throw new LoginAlreadyTakenException();
            }
            throw exception;
        }
        profiles.saveAndFlush(new PlayerProfile(id,name,now));
        return new RegisterResponse(id,login,name);
    }
    @Transactional(readOnly=true)
    public PlayerProfileResponse getProfile(UUID accountId) {
        var account=accounts.findById(accountId).filter(Account::enabled).orElseThrow(AuthenticationFailedException::new);
        var profile=profiles.findById(accountId).orElseThrow(() -> new IllegalStateException("Account profile missing"));
        return new PlayerProfileResponse(accountId,account.login(),profile.displayName(),profile.accountXp(),profile.appearanceId());
    }
}
