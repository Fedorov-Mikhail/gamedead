package com.gamedead.server.auth;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AuthRateLimitTest {
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-10-15T12:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @Test void enforcesSeparateOperationLimitsAndResetsExpiredWindows() {
        var clock=new MutableClock(); var limiter=new AuthAttemptLimiter(clock,100);
        for(int i=0;i<10;i++) assertThat(limiter.tryAcquire("a","login").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a","login").allowed()).isFalse();
        assertThat(limiter.tryAcquire("a","register").allowed()).isTrue();
        assertThat(limiter.tryAcquire("b","login").allowed()).isTrue();
        clock.now=clock.now.plusSeconds(60);
        assertThat(limiter.tryAcquire("a","login").allowed()).isTrue();
    }
    @Test void registrationAllowsOnlyFiveAndReturnsRetryAfter() {
        var limiter=new AuthAttemptLimiter(new MutableClock(),100);
        for(int i=0;i<5;i++) assertThat(limiter.tryAcquire("a","register").allowed()).isTrue();
        var denied=limiter.tryAcquire("a","register");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(1L,60L);
    }
    @Test void capacityCannotGrowAndExpiredKeysFreeSpace() {
        var clock=new MutableClock(); var limiter=new AuthAttemptLimiter(clock,2);
        assertThat(limiter.tryAcquire("a","login").allowed()).isTrue();
        assertThat(limiter.tryAcquire("b","login").allowed()).isTrue();
        assertThat(limiter.tryAcquire("c","login").allowed()).isFalse();
        assertThat(limiter.tryAcquire("a","login").allowed()).isTrue();
        clock.now=clock.now.plusSeconds(60);
        assertThat(limiter.tryAcquire("c","login").allowed()).isTrue();
    }
}
