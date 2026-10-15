package com.gamedead.server.config;
import com.gamedead.server.auth.*;
import com.gamedead.server.error.ApiErrorWriter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.userdetails.*;
import org.springframework.http.HttpMethod;
@Configuration
public class SecurityConfiguration {
    @Bean UserDetailsService noPasswordUserDetailsService(){
        return username->{throw new UsernameNotFoundException("Bearer authentication required");};
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,SessionService sessions,ApiErrorWriter errors,AuthAttemptLimiter limiter) {
        http.csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(config->config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(config->config
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/register","/api/v1/auth/login").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(config->config.authenticationEntryPoint((request,response,exception)->errors.unauthorized(response))
                .accessDeniedHandler((request,response,exception)->errors.write(response,403,"FORBIDDEN","Доступ запрещён")))
            .addFilterBefore(new BearerAuthenticationFilter(sessions,errors),UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new AuthRateLimitFilter(limiter,errors),BearerAuthenticationFilter.class)
            .addFilterBefore(new AuthRequestSizeFilter(errors),AuthRateLimitFilter.class);
        return http.build();
    }
}
