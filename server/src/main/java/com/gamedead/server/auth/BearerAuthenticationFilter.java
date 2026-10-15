package com.gamedead.server.auth;
import com.gamedead.server.error.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final ApiErrorWriter errors;
    public BearerAuthenticationFilter(SessionService sessions,ApiErrorWriter errors){this.sessions=sessions;this.errors=errors;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var headers=Collections.list(request.getHeaders("Authorization"));
        if(headers.isEmpty()){chain.doFilter(request,response);return;}
        if(headers.size()!=1 || !headers.getFirst().regionMatches(true,0,"Bearer ",0,7)){errors.unauthorized(response);return;}
        AccountPrincipal principal;
        try {principal=sessions.authenticate(headers.getFirst().substring(7));}
        catch(AuthenticationFailedException exception){errors.unauthorized(response);return;}
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        SecurityContextHolder.setContext(context);
        try {chain.doFilter(request,response);}
        finally {SecurityContextHolder.clearContext();}
    }
}
