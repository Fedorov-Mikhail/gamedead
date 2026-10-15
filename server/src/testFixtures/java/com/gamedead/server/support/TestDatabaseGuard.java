package com.gamedead.server.support;

public final class TestDatabaseGuard {
    private TestDatabaseGuard() {}
    public static void requireTestDatabase(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.matches("jdbc:postgresql://[a-zA-Z0-9.-]+(?::[0-9]{1,5})?/gamedead_test")) {
            throw new IllegalArgumentException("Integration tests require a direct PostgreSQL URL ending with /gamedead_test (no URL options)");
        }
    }
}
