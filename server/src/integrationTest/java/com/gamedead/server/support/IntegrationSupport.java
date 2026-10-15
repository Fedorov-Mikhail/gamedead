package com.gamedead.server.support;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class IntegrationSupport {
    @LocalServerPort protected int port;
    protected static final HttpClient HTTP=HttpClient.newHttpClient();
    protected static final JsonMapper JSON=JsonMapper.builder().build();
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("GAMEDEAD_TEST_DB_URL");
        TestDatabaseGuard.requireTestDatabase(url);
        String user=required("GAMEDEAD_TEST_DB_USERNAME"), password=required("GAMEDEAD_TEST_DB_PASSWORD");
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.username",()->user);
        registry.add("spring.datasource.password",()->password);
    }
    private static String required(String name) {
        String value=System.getenv(name);
        if(value==null) throw new IllegalStateException("Set "+name+" locally for integration tests");
        return value;
    }
    protected HttpResponse<String> request(String method,String path,String body,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(body!=null) builder.header("Content-Type","application/json");
        builder.method(method, body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    protected HttpResponse<String> register(String login) throws Exception {
        return request("POST","/api/v1/auth/register",
            JSON.writeValueAsString(Map.of("login",login,"displayName","Маг "+login,"password","Test password 123")),null);
    }
    protected HttpResponse<String> login(String login) throws Exception {
        return request("POST","/api/v1/auth/login",
            JSON.writeValueAsString(Map.of("login",login,"password","Test password 123")),null);
    }
    protected String field(String json,String name) {return JSON.readTree(json).path(name).asString();}
}
