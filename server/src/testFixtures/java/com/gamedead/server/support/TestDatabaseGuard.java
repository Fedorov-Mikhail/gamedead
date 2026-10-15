package com.gamedead.server.support;

import java.util.Map;

public final class TestDatabaseGuard {
    private TestDatabaseGuard() {}
    public static void requireTestDatabase(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.matches("jdbc:postgresql://[a-zA-Z0-9.-]+(?::[0-9]{1,5})?/gamedead_test")) {
            throw new IllegalArgumentException("Integration tests require a direct PostgreSQL URL ending with /gamedead_test (no URL options)");
        }
    }
    public static Map<String,Object> connectionProperties(String url,String user,String password) {
        requireTestDatabase(url);
        if(user==null || password==null) throw new IllegalArgumentException("Set dedicated test database credentials locally");
        // Pin every independent connection, not just Spring's primary datasource.
        return Map.of(
            "spring.datasource.url",url,
            "spring.datasource.username",user,
            "spring.datasource.password",password,
            "spring.datasource.hikari.jdbc-url",url,
            "spring.datasource.hikari.username",user,
            "spring.datasource.hikari.password",password,
            "spring.flyway.url",url,
            "spring.flyway.user",user,
            "spring.flyway.password",password
        );
    }
}
