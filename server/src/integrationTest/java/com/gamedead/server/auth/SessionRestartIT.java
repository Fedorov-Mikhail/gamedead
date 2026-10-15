package com.gamedead.server.auth;
import com.gamedead.server.GameDeadServerApplication;
import com.gamedead.server.support.TestDatabaseGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class SessionRestartIT {
    private final HttpClient client=HttpClient.newHttpClient();
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void inheritedFlywayConnectionCannotOverrideTestDatabase() throws Exception {
        String previous=System.getProperty("spring.flyway.url");
        System.setProperty("spring.flyway.url","jdbc:postgresql://127.0.0.1:1/gamedead");
        try(var context=start();var connection=context.getBean(org.flywaydb.core.Flyway.class).getConfiguration().getDataSource().getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("gamedead_test");
        } finally {
            if(previous==null) System.clearProperty("spring.flyway.url");
            else System.setProperty("spring.flyway.url",previous);
        }
    }
    @Test void tokenRemainsValidAfterServerRestart() throws Exception {
        String login="restart_"+UUID.randomUUID().toString().substring(0,8);
        String token;
        try(var first=start()) {
            var registered=send(first,"POST","/api/v1/auth/register",json.writeValueAsString(Map.of("login",login,"displayName","Restart Mage","password","Test password 123")),null);
            assertThat(registered.statusCode()).isEqualTo(201);
            var signedIn=send(first,"POST","/api/v1/auth/login",json.writeValueAsString(Map.of("login",login,"password","Test password 123")),null);
            assertThat(signedIn.statusCode()).isEqualTo(200);
            token=json.readTree(signedIn.body()).path("accessToken").asString();
        }
        try(var second=start()) {
            var profile=send(second,"GET","/api/v1/me",null,token);
            assertThat(profile.statusCode()).isEqualTo(200);
            assertThat(json.readTree(profile.body()).path("login").asString()).isEqualTo(login);
        }
    }
    private ConfigurableApplicationContext start() {
        String url=System.getenv("GAMEDEAD_TEST_DB_URL");
        TestDatabaseGuard.requireTestDatabase(url);
        var properties=new HashMap<>(TestDatabaseGuard.connectionProperties(url,
            System.getenv("GAMEDEAD_TEST_DB_USERNAME"),System.getenv("GAMEDEAD_TEST_DB_PASSWORD")));
        properties.put("server.port",0);
        properties.put("spring.main.banner-mode","off");
        properties.put("logging.level.root","WARN");
        var app=new SpringApplication(GameDeadServerApplication.class);
        app.addInitializers(context->context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("guarded-test-database",properties)));
        return app.run();
    }
    private HttpResponse<String> send(ConfigurableApplicationContext context,String method,String path,String body,String token) throws Exception {
        int port=context.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(body!=null) builder.header("Content-Type","application/json");
        if(token!=null) builder.header("Authorization","Bearer "+token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
}
