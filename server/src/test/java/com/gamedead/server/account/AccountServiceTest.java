package com.gamedead.server.account;

import com.gamedead.protocol.auth.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    @Test void registrationPersistsNormalizedIdentityAndPasswordHashWithDefaultProfile() {
        var accounts=mock(AccountRepository.class);
        var profiles=mock(PlayerProfileRepository.class);
        var encoder=new BCryptPasswordEncoder(4);
        var now=Instant.parse("2026-10-15T12:00:00Z");
        Account[] saved=new Account[1]; PlayerProfile[] profile=new PlayerProfile[1];
        when(accounts.saveAndFlush(any())).thenAnswer(inv -> saved[0]=inv.getArgument(0));
        when(profiles.saveAndFlush(any())).thenAnswer(inv -> profile[0]=inv.getArgument(0));
        var service=new AccountService(accounts,profiles,new AccountInputPolicy(),encoder,Clock.fixed(now,ZoneOffset.UTC));
        var result=service.register(new RegisterRequest("  MaGe  ","  Первый маг  "," secret 123 "));
        assertThat(result.login()).isEqualTo("mage");
        assertThat(result.displayName()).isEqualTo("Первый маг");
        assertThat(saved[0].passwordHash()).isNotEqualTo(" secret 123 ");
        assertThat(encoder.matches(" secret 123 ",saved[0].passwordHash())).isTrue();
        assertThat(encoder.matches("secret 123",saved[0].passwordHash())).isFalse();
        assertThat(saved[0].createdAt()).isEqualTo(now);
        when(accounts.findById(result.accountId())).thenReturn(Optional.of(saved[0]));
        when(profiles.findById(result.accountId())).thenReturn(Optional.of(profile[0]));
        var me=service.getProfile(result.accountId());
        assertThat(me.accountXp()).isZero();
        assertThat(me.appearanceId()).isEqualTo("mage_default");
        assertThat(new RegisterRequest("mage","Mage","sensitive-value").toString()).doesNotContain("sensitive-value");
    }
}
