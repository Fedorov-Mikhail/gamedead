package com.gamedead.server.auth;
import com.gamedead.server.support.IntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import java.net.URI;
import java.net.http.*;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.*;
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AuthHttpLimitsIT extends IntegrationSupport {
    @Test void httpLimitsCoverMalformedLargeAndChunkedRequests() throws Exception {
        assertThat(request("POST","/api/v1/auth/register","{",null).statusCode()).isEqualTo(400);
        assertThat(request("POST","/api/v1/auth/register","x".repeat(8193),null).statusCode()).isEqualTo(413);
        assertThat(request("POST","/api/v1/auth/%72egister","x".repeat(8193),null).statusCode()).isEqualTo(413);
        var chunked=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/register"))
            .header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofInputStream(()->new ByteArrayInputStream(new byte[8193]))).build();
        assertThat(HTTP.send(chunked,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
        for(int i=0;i<10;i++) {
            var response=request("POST","/api/v1/auth/login","{\"login\":\"missing_user\",\"password\":\"secret_wrong_password\"}",null);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).doesNotContain("secret_wrong_password");
        }
        var blocked=login("missing_user");
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
        assertThat(field(blocked.body(),"code")).isEqualTo("RATE_LIMITED");
        assertThat(request("POST","/api/v1/auth/%6cogin","{\"login\":\"missing_user\",\"password\":\"Wrong password\"}",null).statusCode()).isEqualTo(429);
    }
}
