package com.gamedead.server.config;

import com.gamedead.server.support.TestDatabaseGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TestDatabaseGuardTest {
    @Test void acceptsDedicatedDatabase() {
        assertThatCode(() -> TestDatabaseGuard.requireTestDatabase("jdbc:postgresql://localhost:5432/gamedead_test")).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings = {
        "jdbc:postgresql://localhost:5432/gamedead", "jdbc:postgresql://localhost/",
        "jdbc:postgresql:gamedead_test", "jdbc:postgresql://a:5432,b:5432/gamedead_test",
        "jdbc:postgresql://localhost/gamedead_test?options=-csearch_path=game", ""
    }) void rejectsUnsafeTarget(String url) {
        assertThatThrownBy(() -> TestDatabaseGuard.requireTestDatabase(url)).isInstanceOf(IllegalArgumentException.class);
    }
}
