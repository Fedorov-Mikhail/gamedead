package com.gamedead.server.auth;
import jakarta.servlet.http.HttpServletRequest;
final class AuthEndpoints {
    private AuthEndpoints() {}
    static String operation(HttpServletRequest request) {
        if(!"POST".equals(request.getMethod())) return null;
        // Servlet paths are decoded by the container, just like controller routes.
        return switch(request.getServletPath()) {
            case "/api/v1/auth/login" -> "login";
            case "/api/v1/auth/register" -> "register";
            default -> null;
        };
    }
}
