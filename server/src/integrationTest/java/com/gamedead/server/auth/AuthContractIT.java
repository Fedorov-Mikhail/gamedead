package com.gamedead.server.auth;
import com.gamedead.server.support.IntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AuthContractIT extends IntegrationSupport {
    @Test void invalidInputsDuplicatesAndHttpErrorsHaveSafeJson() throws Exception {
        var invalid=request("POST","/api/v1/auth/register","{\"login\":\"ab\",\"displayName\":\"Mage\",\"password\":\"secret-test-123\"}",null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(field(invalid.body(),"code")).isEqualTo("INVALID_INPUT");
        assertThat(invalid.body()).contains("fieldErrors","login").doesNotContain("secret-test-123");
        assertThat(request("POST","/api/v1/auth/register","{}",null).statusCode()).isEqualTo(400);
        String login="contract_"+UUID.randomUUID().toString().substring(0,8);
        String unicodePassword=" Секретный пароль 🔥 ";
        String registration=JSON.writeValueAsString(java.util.Map.of("login",login,"displayName","Маг","password",unicodePassword));
        assertThat(request("POST","/api/v1/auth/register",registration,null).statusCode()).isEqualTo(201);
        assertThat(register(login.toUpperCase(java.util.Locale.ROOT)).statusCode()).isEqualTo(409);
        var signedIn=request("POST","/api/v1/auth/login",JSON.writeValueAsString(java.util.Map.of("login",login,"password",unicodePassword)),null);
        assertThat(signedIn.statusCode()).isEqualTo(200);
        assertThat(signedIn.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        String token=field(signedIn.body(),"accessToken");
        assertThat(request("GET","/api/v1/me",null,"x".repeat(43)).statusCode()).isEqualTo(401);
        var unsupported=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/register"))
            .header("Content-Type","text/plain").POST(HttpRequest.BodyPublishers.ofString("not-json")).build();
        var response=HTTP.send(unsupported,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(field(response.body(),"code")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        var method=request("POST","/api/v1/me",null,token);
        assertThat(method.statusCode()).isEqualTo(405);
    }
}
