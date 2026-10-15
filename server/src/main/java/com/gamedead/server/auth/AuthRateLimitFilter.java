package com.gamedead.server.auth;
import com.gamedead.server.error.ApiErrorWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthAttemptLimiter limiter;
    private final ApiErrorWriter errors;
    public AuthRateLimitFilter(AuthAttemptLimiter limiter,ApiErrorWriter errors){this.limiter=limiter;this.errors=errors;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String operation=AuthEndpoints.operation(request);
        if(operation!=null) {
            var decision=limiter.tryAcquire(request.getRemoteAddr(),operation);
            if(!decision.allowed()) {
                response.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));
                errors.write(response,429,"RATE_LIMITED","Слишком много попыток. Повтори позже");
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
