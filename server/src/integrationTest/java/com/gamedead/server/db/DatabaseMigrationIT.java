package com.gamedead.server.db;

import com.gamedead.server.support.TestDatabaseGuard;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DatabaseMigrationIT {
    @Test void migrationIsRepeatableAndEnforcesAccountConstraints() throws Exception {
        String url = System.getenv("GAMEDEAD_TEST_DB_URL");
        TestDatabaseGuard.requireTestDatabase(url);
        String user = System.getenv("GAMEDEAD_TEST_DB_USERNAME");
        String password = System.getenv("GAMEDEAD_TEST_DB_PASSWORD");
        if (user == null || password == null) throw new IllegalStateException("Set GAMEDEAD_TEST_DB_USERNAME and GAMEDEAD_TEST_DB_PASSWORD locally");
        Flyway flyway = Flyway.configure().dataSource(url, user, password)
            .defaultSchema("game").schemas("game").createSchemas(false).cleanDisabled(true).load();
        flyway.migrate();
        assertThat(flyway.info().current()).isNotNull();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            var id = UUID.randomUUID();
            try (var insert = connection.prepareStatement("INSERT INTO game.accounts(id,login,password_hash,created_at) VALUES (?,?,?,now())")) {
                insert.setObject(1, id); insert.setString(2, "migration_" + id.toString().substring(0, 8));
                insert.setString(3, "test-only-not-a-password"); insert.executeUpdate();
            }
            var savepoint = connection.setSavepoint();
            try (var invalid = connection.prepareStatement("INSERT INTO game.player_profiles(account_id,display_name,account_xp,updated_at) VALUES (?, 'Mage', -1, now())")) {
                invalid.setObject(1, id);
                assertThatThrownBy(invalid::executeUpdate).isInstanceOf(SQLException.class);
            }
            connection.rollback(savepoint);
            try (var valid = connection.prepareStatement("INSERT INTO game.player_profiles(account_id,display_name,updated_at) VALUES (?, 'Mage', now())")) {
                valid.setObject(1, id); assertThat(valid.executeUpdate()).isEqualTo(1);
            }
            connection.rollback();
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
