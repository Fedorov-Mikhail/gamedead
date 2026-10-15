package com.gamedead.server.account;
import com.gamedead.server.support.IntegrationSupport;
import com.gamedead.protocol.auth.RegisterRequest;
import com.gamedead.server.error.LoginAlreadyTakenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class AccountRegistrationIT extends IntegrationSupport {
    @Autowired AccountService service;
    @Autowired JdbcTemplate jdbc;
    @Test void concurrentRegistrationsHaveOneWinnerAndOneCompleteProfile() throws Exception {
        String name="race_"+UUID.randomUUID().toString().substring(0,8);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->attempt(name,gate));
            var second=pool.submit(()->attempt(name.toUpperCase(Locale.ROOT),gate));
            gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("created","conflict");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game.accounts a JOIN game.player_profiles p ON p.account_id=a.id WHERE a.login=?",Integer.class,name)).isEqualTo(1);
    }
    private String attempt(String name,CountDownLatch gate) throws Exception {
        gate.await();
        try {service.register(new RegisterRequest(name,"Race Mage","Test password 123")); return "created";}
        catch(LoginAlreadyTakenException expected) {return "conflict";}
    }
    @Test void profileDatabaseFailureRollsBackAccountInsert() {
        String name="rollback_"+UUID.randomUUID().toString().substring(0,8);
        jdbc.execute("ALTER TABLE game.player_profiles ADD CONSTRAINT test_reject_profile CHECK(display_name <> 'RollbackMage')");
        try {
            assertThatThrownBy(()->service.register(new RegisterRequest(name,"RollbackMage","Test password 123"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM game.accounts WHERE login=?",Integer.class,name)).isZero();
        } finally {jdbc.execute("ALTER TABLE game.player_profiles DROP CONSTRAINT test_reject_profile");}
    }
}
