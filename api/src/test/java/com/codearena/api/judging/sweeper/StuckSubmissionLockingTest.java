package com.codearena.api.judging.sweeper;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.support.DockerAvailability;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs the sweeper's real claim SQL against PostgreSQL 16 with the real Flyway schema, using plain JDBC so two
 * concurrent transactions can be controlled precisely. Pulled forward from Phase 6.
 */
class StuckSubmissionLockingTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    private static final String CLAIM_SQL = SubmissionRepository.LOCK_STUCK_SQL
            .replace(":cutoff", "?")
            .replace(":limit", "?");

    private long stalePending;
    private long staleRunning;
    private long staleRequeued;

    @BeforeAll
    static void startPostgres() {
        DockerAvailability.requireDocker("sweeper postgres test");
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void seedSubmissions() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("delete from submissions");
            st.execute("delete from users");
            st.execute("insert into users (username, email, password_hash) values ('alice', 'a@example.com', 'x')");
            stalePending = insert(c, "PENDING", "now() - interval '10 minutes'", "null");
            staleRunning = insert(c, "RUNNING", "now() - interval '10 minutes'", "now() - interval '5 minutes'");
            insert(c, "PENDING", "now() - interval '10 minutes'", "now() - interval '30 seconds'");
            insert(c, "ACCEPTED", "now() - interval '10 minutes'", "now() - interval '10 minutes'");
            insert(c, "WRONG_ANSWER", "now() - interval '10 minutes'", "null");
            staleRequeued = insert(c, "PENDING", "now() - interval '10 minutes'", "now() - interval '3 minutes'");
        }
    }

    @Test
    void claimsOnlyOpenSubmissionsQueuedLongerThanCutoff() throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);

            assertThat(claim(c, 10)).containsExactly(stalePending, staleRunning, staleRequeued);
            c.rollback();
        }
    }

    @Test
    void concurrentClaimsSkipLockedRowsInsteadOfWaiting() throws SQLException {
        try (Connection first = connect(); Connection second = connect()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);

            List<Long> claimedByFirst = claim(first, 2);
            List<Long> claimedBySecond = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> claim(second, 10));

            assertThat(claimedByFirst).containsExactly(stalePending, staleRunning);
            assertThat(claimedBySecond).containsExactly(staleRequeued);
            second.rollback();
            first.rollback();
        }
    }

    @Test
    void rowsBecomeClaimableAgainWhenTheLockIsReleased() throws SQLException {
        try (Connection first = connect(); Connection second = connect()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            assertThat(claim(first, 10)).hasSize(3);
            assertThat(claim(second, 10)).isEmpty();

            first.rollback();

            assertThat(claim(second, 10)).containsExactly(stalePending, staleRunning, staleRequeued);
            second.rollback();
        }
    }

    private static List<Long> claim(Connection connection, int limit) throws SQLException {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(2));
        try (PreparedStatement ps = connection.prepareStatement(CLAIM_SQL)) {
            ps.setObject(1, OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
            ps.setInt(2, limit);
            List<Long> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
            }
            return ids;
        }
    }

    private static long insert(Connection c, String status, String createdAt, String lastQueuedAt) throws SQLException {
        String sql = "insert into submissions (user_id, problem_id, language, source_code, status, created_at, "
                + "last_queued_at) values ((select id from users where username = 'alice'), "
                + "(select id from problems where slug = 'two-sum'), 'PYTHON', 'print(1)', '" + status + "', "
                + createdAt + ", " + lastQueuedAt + ") returning id";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
