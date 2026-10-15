package com.gamedead.server.auth;
import com.gamedead.server.error.ApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class AuthRequestSizeTest {
    private final AuthRequestSizeFilter filter=new AuthRequestSizeFilter(new ApiErrorWriter(JsonMapper.builder().build()));
    @Test void rejectsKnownOversizedBodyWithoutReachingController() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/auth/register");
        request.setServletPath("/api/v1/auth/register");
        request.setContent(new byte[8193]);
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->{throw new AssertionError("Oversized body reached controller");});
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    }
    @Test void limitsUnknownLengthBodyAndPreservesExactBoundary() throws Exception {
        var chunked=new MockHttpServletRequest("POST","/api/v1/auth/login") {
            @Override public int getContentLength(){return -1;}
            @Override public long getContentLengthLong(){return -1;}
        };
        chunked.setServletPath("/api/v1/auth/login");
        chunked.setContent(new byte[8193]);
        var denied=new MockHttpServletResponse();
        filter.doFilter(chunked,denied,(req,res)->{throw new AssertionError("Chunked limit bypass");});
        assertThat(denied.getStatus()).isEqualTo(413);
        var exact=new MockHttpServletRequest("POST","/api/v1/auth/login");
        exact.setServletPath("/api/v1/auth/login");
        byte[] body="x".repeat(8192).getBytes(StandardCharsets.UTF_8); exact.setContent(body);
        var allowed=new MockHttpServletResponse();
        filter.doFilter(exact,allowed,(req,res)->{
            assertThat(req.getInputStream().readAllBytes()).isEqualTo(body);
            ((jakarta.servlet.http.HttpServletResponse)res).setStatus(204);
        });
        assertThat(allowed.getStatus()).isEqualTo(204);
    }
}
