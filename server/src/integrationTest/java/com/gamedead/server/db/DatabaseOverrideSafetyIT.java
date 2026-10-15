package com.gamedead.server.db;
import com.gamedead.server.support.IntegrationSupport;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;
@TestPropertySource(properties={
    "spring.flyway.url=jdbc:postgresql://127.0.0.1:1/gamedead",
    "spring.datasource.hikari.jdbc-url=jdbc:postgresql://127.0.0.1:1/gamedead"
})
class DatabaseOverrideSafetyIT extends IntegrationSupport {
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Test void independentConnectionOverridesCannotEscapeTestDatabase() throws Exception {
        try(var migration=flyway.getConfiguration().getDataSource().getConnection(); var application=dataSource.getConnection()) {
            assertThat(migration.getCatalog()).isEqualTo("gamedead_test");
            assertThat(application.getCatalog()).isEqualTo("gamedead_test");
        }
    }
}
