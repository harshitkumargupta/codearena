package com.codearena.api.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codearena.api.support.DockerAvailability;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V4 migration against real PostgreSQL: hints carry level 1-3, explanations carry no level.
 */
class HintLogSchemaTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void startPostgres() throws SQLException {
        DockerAvailability.requireDocker("hint log schema test");
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("insert into users (username, email, password_hash) values ('alice', 'a@example.com', 'x')");
        }
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Test
    void hintAndExplanationRowsAreAccepted() throws SQLException {
        insert("'HINT'", "3");
        insert("'EXPLANATION'", "null");

        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select kind, level from hint_logs order by id")) {
            rs.next();
            assertThat(rs.getString(1) + rs.getInt(2)).isEqualTo("HINT3");
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("EXPLANATION");
            assertThat(rs.getObject(2)).isNull();
        }
    }

    @Test
    void invalidRowsAreRejectedByConstraints() {
        assertThatThrownBy(() -> insert("'HINT'", "4")).isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_hint_logs_level");
        assertThatThrownBy(() -> insert("'HINT'", "null")).isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_hint_logs_level");
        assertThatThrownBy(() -> insert("'EXPLANATION'", "2")).isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_hint_logs_level");
        assertThatThrownBy(() -> insert("'CHAT'", "null")).isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_hint_logs_kind");
    }

    private static void insert(String kind, String level) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("insert into hint_logs (user_id, problem_id, kind, level) values ("
                    + "(select id from users where username = 'alice'), (select id from problems where slug = 'two-sum'), "
                    + kind + ", " + level + ")");
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
