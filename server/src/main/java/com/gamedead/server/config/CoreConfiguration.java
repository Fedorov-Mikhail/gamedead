package com.gamedead.server.config;
import com.gamedead.server.account.AccountInputPolicy;
import com.gamedead.server.auth.*;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Clock;
import java.security.SecureRandom;
import java.util.Map;
@Configuration
public class CoreConfiguration {
    @Bean Clock clock(){return Clock.systemUTC();}
    @Bean SecureRandom secureRandom(){return new SecureRandom();}
    @Bean AccountInputPolicy accountInputPolicy(){return new AccountInputPolicy();}
    @Bean TokenCodec tokenCodec(SecureRandom random){return new TokenCodec(random);}
    @Bean PasswordEncoder passwordEncoder(){
        return new DelegatingPasswordEncoder("bcrypt",Map.of("bcrypt",new BCryptPasswordEncoder(12)));
    }
    @Bean AuthAttemptLimiter authAttemptLimiter(Clock clock){return new AuthAttemptLimiter(clock,10000);}
}
