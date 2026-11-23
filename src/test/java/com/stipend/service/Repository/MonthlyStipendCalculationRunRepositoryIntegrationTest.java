package com.stipend.service.Repository;

import com.stipend.service.Model.MonthlyStipendCalculationRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MonthlyStipendCalculationRunRepositoryIntegrationTest {

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
    private MonthlyStipendCalculationRunRepository runRepository;

    @Test
    void onlyOneInstanceCanClaimSamePeriodOnSameRunDate() {
        YearMonth period = YearMonth.of(2026, 10);
        LocalDate runDate = LocalDate.of(2026, 11, 2);

        int firstClaim = runRepository.claimRun(
                UUID.randomUUID(),
                period.getYear(),
                period.getMonthValue(),
                runDate,
                Instant.parse("2026-11-02T00:00:00Z")
        );
        int duplicateClaim = runRepository.claimRun(
                UUID.randomUUID(),
                period.getYear(),
                period.getMonthValue(),
                runDate,
                Instant.parse("2026-11-02T00:01:00Z")
        );
        runRepository.flush();

        assertThat(firstClaim).isEqualTo(1);
        assertThat(duplicateClaim).isZero();
        assertThat(runRepository.existsByPeriodYearAndPeriodMonth(2026, 10)).isTrue();
    }

    @Test
    void samePeriodCanBeClaimedOnDifferentRunDatesForLateEventRecalculation() {
        YearMonth period = YearMonth.of(2026, 10);

        int firstDayClaim = runRepository.claimRun(
                UUID.randomUUID(),
                period.getYear(),
                period.getMonthValue(),
                LocalDate.of(2026, 11, 2),
                Instant.parse("2026-11-02T00:00:00Z")
        );
        int nextDayClaim = runRepository.claimRun(
                UUID.randomUUID(),
                period.getYear(),
                period.getMonthValue(),
                LocalDate.of(2026, 11, 3),
                Instant.parse("2026-11-03T00:00:00Z")
        );
        runRepository.flush();

        assertThat(firstDayClaim).isEqualTo(1);
        assertThat(nextDayClaim).isEqualTo(1);
    }
}
