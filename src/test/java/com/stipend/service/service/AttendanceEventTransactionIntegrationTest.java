package com.stipend.service.service;

import com.stipend.service.Enum.AttendanceStatus;
import com.stipend.service.Event.AttendanceEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AttendanceEventServiceImpl.class, ProcessedEventService.class})
@Testcontainers(disabledWithoutDocker = true)
class AttendanceEventTransactionIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AttendanceEventServiceImpl attendanceEventService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void processingRollsBackAttendanceWhenProcessedEventInsertFails() {
        UUID eventId = UUID.randomUUID();
        UUID attendanceId = UUID.randomUUID();
        AttendanceEvent event = new AttendanceEvent(
                eventId,
                attendanceId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                AttendanceStatus.PRESENT,
                LocalDate.of(2026, 10, 12),
                Instant.parse("2026-10-12T12:00:00Z"),
                1
        );

        jdbcTemplate.execute("""
                CREATE FUNCTION reject_processed_event_insert()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM stipend_attendance
                        WHERE attendance_id = '%s'::uuid
                    ) THEN
                        RAISE EXCEPTION 'forced processed event insert failure'
                            USING ERRCODE = '23514';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(attendanceId));
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_processed_event_insert
                BEFORE INSERT ON processed_events
                FOR EACH ROW
                EXECUTE FUNCTION reject_processed_event_insert()
                """);

        try {
            assertThatThrownBy(() -> attendanceEventService.processAttendanceEvent(event))
                    .satisfies(exception ->
                            assertThat(hasSqlState(exception, "23514")).isTrue());

            Integer attendanceCount = jdbcTemplate.queryForObject(
                    "select count(*) from stipend_attendance where attendance_id = ?",
                    Integer.class,
                    attendanceId
            );
            Integer processedEventCount = jdbcTemplate.queryForObject(
                    "select count(*) from processed_events where event_id = ?",
                    Integer.class,
                    eventId
            );

            assertThat(attendanceCount).isZero();
            assertThat(processedEventCount).isZero();
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS reject_processed_event_insert ON processed_events"
            );
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_processed_event_insert()");
        }
    }

    private boolean hasSqlState(Throwable throwable, String expectedSqlState) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && expectedSqlState.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
