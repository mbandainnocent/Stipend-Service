package com.stipend.service.Repository;

import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Enum.StudentStatus;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Model.MonthlyStipendFinanceView;
import com.stipend.service.Model.StipendPolicy;
import com.stipend.service.Model.StipendTier;
import com.stipend.service.Model.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FinanceRepositoryIntegrationTest {

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
    private MonthlyStipendRepository monthlyStipendRepository;

    @Autowired
    private StipendPolicyRepository stipendPolicyRepository;

    @Autowired
    private StudentRegistrationRepository studentRepository;

    @Test
    void searchesStipendsUsingFinanceFiltersAndPagination() {
        UUID cohortId = UUID.randomUUID();
        UUID studentId = saveStudent("Ava", "Mugenzi", cohortId);
        UUID otherStudentId = saveStudent("Noah", "Uwase", UUID.randomUUID());
        saveStipend(studentId, cohortId, 2026, 8, StipendStatus.CALCULATED);
        saveStipend(studentId, cohortId, 2026, 9, StipendStatus.PAID);
        saveStipend(otherStudentId, UUID.randomUUID(), 2026, 8, StipendStatus.CALCULATED);

        Page<MonthlyStipend> page = monthlyStipendRepository.search(
                2026,
                8,
                cohortId,
                studentId,
                StipendStatus.CALCULATED,
                PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement().satisfies(stipend -> {
            assertThat(stipend.getStudentId()).isEqualTo(studentId);
            assertThat(stipend.getStipendMonth()).isEqualTo(8);
            assertThat(stipend.getStatus()).isEqualTo(StipendStatus.CALCULATED);
        });
    }

    @Test
    void financeProjectionJoinsStudentDetailsAndDerivesPeriodBounds() {
        UUID cohortId = UUID.randomUUID();
        UUID studentId = saveStudent("Ava", "Mugenzi", cohortId);
        saveStipend(studentId, cohortId, 2026, 8, StipendStatus.CALCULATED);

        Page<MonthlyStipendFinanceView> page = monthlyStipendRepository.searchFinanceView(
                2026,
                8,
                cohortId,
                null,
                null,
                PageRequest.of(0, 10, Sort.by("stipendYear").descending())
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        MonthlyStipendFinanceView view = page.getContent().get(0);
        assertThat(view.firstName()).isEqualTo("Ava");
        assertThat(view.lastName()).isEqualTo("Mugenzi");
        assertThat(view.cohortId()).isEqualTo(cohortId);
        assertThat(view.cohortStartDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(view.cohortEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(view.scheduledTrainingDays()).isNull();
        assertThat(view.calculatedAmount()).isEqualByComparingTo("20000.00");
        assertThat(view.currency()).isEqualTo("RWF");
        assertThat(view.stipendPeriodStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(view.stipendPeriodEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void searchesPoliciesByProgramCohortAndEnabledAndLoadsTiers() {
        UUID programId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        savePolicy(programId, cohortId, true);
        savePolicy(programId, UUID.randomUUID(), true);
        savePolicy(programId, cohortId, false);

        Page<StipendPolicy> page = stipendPolicyRepository.searchPolicies(
                programId,
                cohortId,
                true,
                PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        StipendPolicy policy = page.getContent().get(0);
        assertThat(policy.isEnabled()).isTrue();
        assertThat(policy.getProgramId()).isEqualTo(programId);
        assertThat(policy.getCohortId()).isEqualTo(cohortId);
        assertThat(policy.getTiers()).hasSize(4);
        assertThat(policy.getTiers())
                .extracting(StipendTier::getPaymentPercentage)
                .containsExactly(
                        new BigDecimal("50.00"),
                        new BigDecimal("75.00"),
                        new BigDecimal("90.00"),
                        new BigDecimal("100.00")
                );
    }

    private UUID saveStudent(String firstName, String lastName, UUID cohortId) {
        UUID studentId = UUID.randomUUID();
        studentRepository.saveAndFlush(Student.builder()
                .studentId(studentId)
                .firstName(firstName)
                .lastName(lastName)
                .programId(UUID.randomUUID())
                .cohortId(cohortId)
                .cohortStartDate(LocalDate.of(2026, 1, 15))
                .cohortEndDate(LocalDate.of(2026, 12, 31))
                .status(StudentStatus.ACTIVE)
                .registeredAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .stipendEligible(true)
                .cohortStipendEligible(true)
                .stipendBaseAmount(new BigDecimal("40000.00"))
                .stipendCurrency("RWF")
                .build());
        return studentId;
    }

    private void saveStipend(
            UUID studentId,
            UUID cohortId,
            int year,
            int month,
            StipendStatus status
    ) {
        monthlyStipendRepository.saveAndFlush(MonthlyStipend.builder()
                .studentId(studentId)
                .cohortId(cohortId)
                .policyId(UUID.randomUUID())
                .stipendYear(year)
                .stipendMonth(month)
                .absenceCount(BigDecimal.ZERO)
                .baseAmount(new BigDecimal("40000.00"))
                .paymentPercentage(new BigDecimal("100.00"))
                .calculatedAmount(new BigDecimal("20000.00"))
                .currency("RWF")
                .status(status)
                .calculatedAt(Instant.parse("2026-09-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-09-01T00:00:00Z"))
                .build());
    }

    private void savePolicy(UUID programId, UUID cohortId, boolean enabled) {
        StipendPolicy policy = StipendPolicy.builder()
                .programId(programId)
                .cohortId(cohortId)
                .enabled(enabled)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .tiers(List.of(
                        tier("0", "50.00"),
                        tier("85", "75.00"),
                        tier("90", "90.00"),
                        tier("95", "100.00")
                ))
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        policy.getTiers().forEach(tier -> tier.setPolicy(policy));
        stipendPolicyRepository.saveAndFlush(policy);
    }

    private StipendTier tier(String minimumAttendance, String percentage) {
        return StipendTier.builder()
                .minimumAttendancePercentage(new BigDecimal(minimumAttendance))
                .paymentPercentage(new BigDecimal(percentage))
                .build();
    }
}
