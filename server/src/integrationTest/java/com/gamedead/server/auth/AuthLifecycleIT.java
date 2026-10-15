package com.gamedead.server.auth;
import com.gamedead.server.support.IntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class AuthLifecycleIT extends IntegrationSupport {
    @Autowired JdbcTemplate jdbc;
    @Test void accountsAreIndependentAndLogoutRevokesOnlyCurrentSession() throws Exception {
        String first="mage_"+UUID.randomUUID().toString().substring(0,8);
        String second="mage_"+UUID.randomUUID().toString().substring(0,8);
        assertThat(register(first).statusCode()).isEqualTo(201);
        assertThat(register(second).statusCode()).isEqualTo(201);
        var signedIn=login(first);
        assertThat(signedIn.statusCode()).isEqualTo(200);
        String token=field(signedIn.body(),"accessToken");
        String other=field(login(second).body(),"accessToken");
        var me=request("GET","/api/v1/me",null,token);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(field(me.body(),"login")).isEqualTo(first);
        assertThat(me.body()).doesNotContain("password","token_hash","passwordHash");
        assertThat(field(request("GET","/api/v1/me",null,other).body(),"login")).isEqualTo(second);
        assertThat(request("GET","/api/v1/me",null,null).statusCode()).isEqualTo(401);
        assertThat(request("POST","/api/v1/auth/logout",null,token).statusCode()).isEqualTo(204);
        assertThat(request("GET","/api/v1/me",null,token).statusCode()).isEqualTo(401);
        assertThat(request("GET","/api/v1/me",null,other).statusCode()).isEqualTo(200);
    }
    @Test void expiredAndDisabledSessionsAreRejected() throws Exception {
        String name="mage_"+UUID.randomUUID().toString().substring(0,8);
        assertThat(register(name).statusCode()).isEqualTo(201);
        String token=field(login(name).body(),"accessToken");
        jdbc.update("UPDATE game.auth_sessions SET created_at=now()-interval '10 hours', expires_at=now()-interval '1 hour' WHERE account_id=(SELECT id FROM game.accounts WHERE login=?)",name);
        assertThat(request("GET","/api/v1/me",null,token).statusCode()).isEqualTo(401);
        String next=field(login(name).body(),"accessToken");
        jdbc.update("UPDATE game.accounts SET enabled=false WHERE login=?",name);
        assertThat(request("GET","/api/v1/me",null,next).statusCode()).isEqualTo(401);
        assertThat(request("GET","/api/v1/me",null,"x".repeat(43)).statusCode()).isEqualTo(401);
        var bad=request("POST","/api/v1/auth/login","{\"login\":\""+name+"\",\"password\":\"Wrong password 123\"}",null);
        var missing=request("POST","/api/v1/auth/login","{\"login\":\"missing_mage\",\"password\":\"Wrong password 123\"}",null);
        assertThat(bad.statusCode()).isEqualTo(401);
        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(bad.body()).isEqualTo(missing.body());
    }
}
