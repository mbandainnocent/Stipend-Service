package com.stipend.service.StipendRecords;

import com.stipend.service.Enum.AttendanceStatus;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Model.AttendanceRecord;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Model.StipendPolicy;
import com.stipend.service.Model.StipendTier;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.AttendanceRecordRepository;
import com.stipend.service.Repository.MonthlyStipendRepository;
import com.stipend.service.Repository.StipendPolicyRepository;
import com.stipend.service.Repository.StudentRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MonthlyStipendService.class, MonthlyStipendConcurrencyIntegrationTest.RepositoryTestConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class MonthlyStipendConcurrencyIntegrationTest {

    private static final YearMonth PERIOD = YearMonth.of(2025, 1);

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
    private StudentRegistrationRepository studentRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRepository;

    @Autowired
    private StipendPolicyRepository policyRepository;

    @Autowired
    private MonthlyStipendRepository stipendRepository;

    @Autowired
    private MonthlyStipendService stipendService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCalculationsCreateOneCompleteMonthlyRecord() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        Student student = studentRepository.save(Student.builder()
                .studentId(studentId)
                .firstName("Test")
                .lastName("Student")
                .programId(programId)
                .cohortId(cohortId)
                .cohortStartDate(LocalDate.of(2025, 1, 1))
                .cohortEndDate(LocalDate.of(2025, 12, 31))
                .registeredAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .stipendEligible(true)
                .cohortStipendEligible(true)
                .stipendBaseAmount(new BigDecimal("40000.00"))
                .stipendCurrency("RWF")
                .build());

        StipendPolicy policy = StipendPolicy.builder()
                .programId(programId)
                .cohortId(cohortId)
                .enabled(true)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .tiers(List.of(StipendTier.builder()
                        .minimumAbsences(0)
                        .maximumAbsences(null)
                        .paymentPercentage(new BigDecimal("100.00"))
                        .build()))
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
        policy.getTiers().forEach(tier -> tier.setPolicy(policy));
        policyRepository.saveAndFlush(policy);

        saveAttendance(student, cohortId, programId, LocalDate.of(2025, 1, 2), AttendanceStatus.PRESENT);
        saveAttendance(student, cohortId, programId, LocalDate.of(2025, 1, 3), AttendanceStatus.ABSENT);
        saveAttendance(student, cohortId, programId, LocalDate.of(2025, 1, 6), AttendanceStatus.PRESENT);
        saveAttendance(student, cohortId, programId, LocalDate.of(2025, 1, 7), AttendanceStatus.EXCUSED);

        RepositoryTestConfiguration.armLookupBarrier();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<List<MonthlyStipend>>> results = List.of(
                    executor.submit(() -> stipendService.calculateMonthlyStipend(PERIOD)),
                    executor.submit(() -> stipendService.calculateMonthlyStipend(PERIOD))
            );
            int successfulCalculations = 0;
            int uniquenessConflicts = 0;
            for (Future<List<MonthlyStipend>> result : results) {
                try {
                    assertThat(result.get(20, TimeUnit.SECONDS)).hasSize(1);
                    successfulCalculations++;
                } catch (ExecutionException exception) {
                    assertThat(hasPostgresUniqueViolation(exception))
                            .withFailMessage("Unexpected calculation failure: %s", exception)
                            .isTrue();
                    uniquenessConflicts++;
                }
            }

            assertThat(successfulCalculations).isEqualTo(1);
            assertThat(uniquenessConflicts).isEqualTo(1);

            List<MonthlyStipend> persisted = stipendRepository
                    .findAll().stream()
                    .filter(record -> record.getStudentId().equals(studentId))
                    .toList();
            assertThat(persisted).hasSize(1);
            MonthlyStipend record = persisted.get(0);
            assertThat(record.getCohortId()).isEqualTo(cohortId);
            assertThat(record.getPolicyId()).isEqualTo(policy.getPolicyId());
            assertThat(record.getStipendYear()).isEqualTo(PERIOD.getYear());
            assertThat(record.getStipendMonth()).isEqualTo(PERIOD.getMonthValue());
            assertThat(record.getAbsenceCount()).isEqualTo(1);
            assertThat(record.getBaseAmount()).isEqualByComparingTo("40000.00");
            assertThat(record.getPaymentPercentage()).isEqualByComparingTo("100.00");
            assertThat(record.getCalculatedAmount()).isEqualByComparingTo("20000.00");
            assertThat(record.getCurrency()).isEqualTo("RWF");
            assertThat(record.getStatus()).isEqualTo(StipendStatus.CALCULATED);
            assertThat(record.getCalculatedAt()).isNotNull();
            assertThat(record.getUpdatedAt()).isNotNull();
        } finally {
            RepositoryTestConfiguration.disarmLookupBarrier();
            executor.shutdownNow();
        }
    }

    private void saveAttendance(
            Student student,
            UUID cohortId,
            UUID programId,
            LocalDate date,
            AttendanceStatus status
    ) {
        attendanceRepository.save(AttendanceRecord.builder()
                .attendanceId(UUID.randomUUID())
                .studentId(student.getStudentId())
                .cohortId(cohortId)
                .programId(programId)
                .date(date)
                .status(status)
                .createdAt(LocalDateTime.of(date, java.time.LocalTime.NOON))
                .updatedAt(LocalDateTime.of(date, java.time.LocalTime.NOON))
                .build());
    }

    private boolean hasPostgresUniqueViolation(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @TestConfiguration
    static class RepositoryTestConfiguration {
        private static volatile CountDownLatch lookupBarrier;
        private static final AtomicInteger EMPTY_LOOKUP_COUNT = new AtomicInteger();

        @Bean
        @Primary
        MonthlyStipendRepository synchronizedMonthlyStipendRepository(
                @Qualifier("monthlyStipendRepository") MonthlyStipendRepository delegate
        ) {
            return (MonthlyStipendRepository) Proxy.newProxyInstance(
                    MonthlyStipendRepository.class.getClassLoader(),
                    new Class<?>[]{MonthlyStipendRepository.class},
                    (proxy, method, args) -> {
                        try {
                            Object result = method.invoke(delegate, args);
                            if (method.getName().equals(
                                    "findByStudentIdAndStipendYearAndStipendMonth"
                            ) && result instanceof Optional<?> existing && existing.isEmpty()) {
                                CountDownLatch barrier = lookupBarrier;
                                if (barrier != null && EMPTY_LOOKUP_COUNT.incrementAndGet() <= 2) {
                                    barrier.countDown();
                                    if (!barrier.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Concurrent calculations did not reach the lookup together"
                                        );
                                    }
                                }
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
            );
        }

        static void armLookupBarrier() {
            EMPTY_LOOKUP_COUNT.set(0);
            lookupBarrier = new CountDownLatch(2);
        }

        static void disarmLookupBarrier() {
            lookupBarrier = null;
        }
    }
}
