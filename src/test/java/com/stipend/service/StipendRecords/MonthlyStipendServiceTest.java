package com.stipend.service.StipendRecords;

import com.stipend.service.Enum.AttendanceStatus;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Enum.StudentStatus;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Model.StipendPolicy;
import com.stipend.service.Model.StipendTier;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.AttendanceRecordRepository;
import com.stipend.service.Repository.MonthlyStipendRepository;
import com.stipend.service.Repository.StipendPolicyRepository;
import com.stipend.service.Repository.StudentRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyStipendServiceTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 5);
    private static final LocalDate PERIOD_START = PERIOD.atDay(1);
    private static final LocalDate PERIOD_END = PERIOD.atEndOfMonth();
    private static final Set<AttendanceStatus> ABSENCE_STATUSES =
            Set.of(AttendanceStatus.ABSENT, AttendanceStatus.LATE);

    @Mock
    private StudentRegistrationRepository studentRepository;

    @Mock
    private AttendanceRecordRepository attendanceRepository;

    @Mock
    private MonthlyStipendRepository stipendRepository;

    @Mock
    private StipendPolicyRepository policyRepository;

    private MonthlyStipendService service;
    private Student student;
    private StipendPolicy policy;

    @BeforeEach
    void setUp() {
        service = new MonthlyStipendService(
                studentRepository,
                attendanceRepository,
                stipendRepository,
                policyRepository
        );
        policy = StipendPolicy.builder()
                .policyId(UUID.randomUUID())
                .tiers(List.of(
                        tier(0, 2, "100.00"),
                        tier(3, 3, "50.00"),
                        tier(4, null, "0.00")
                ))
                .build();
        student = Student.builder()
                .studentId(UUID.randomUUID())
                .cohortId(UUID.randomUUID())
                .cohortStartDate(LocalDate.of(2026, 1, 1))
                .cohortEndDate(LocalDate.of(2026, 12, 31))
                .status(StudentStatus.ACTIVE)
                .stipendEligible(true)
                .stipendBaseAmount(new BigDecimal("50000.00"))
                .stipendCurrency("RWF")
                .build();
        policy.setCohortId(student.getCohortId());

        List<LocalDate> classDates = IntStream.rangeClosed(1, 20)
                .mapToObj(day -> LocalDate.of(2026, 5, day))
                .toList();
        lenient().when(studentRepository.findByStatusAndStipendEligibleTrue(StudentStatus.ACTIVE))
                .thenReturn(List.of(student));
        lenient().when(attendanceRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(classDates);
        lenient().when(attendanceRepository.findFirstAttendanceDateByStudentIdAndCohortIdAndStatusAndDateBetween(
                student.getStudentId(),
                student.getCohortId(),
                AttendanceStatus.PRESENT,
                student.getCohortStartDate(),
                PERIOD_END
        )).thenReturn(Optional.of(PERIOD_START));
        lenient().when(attendanceRepository
                .countDistinctAttendanceDatesByStudentIdAndCohortIdAndStatusAndDateBetween(
                        student.getStudentId(),
                        student.getCohortId(),
                        AttendanceStatus.PRESENT,
                        PERIOD_START,
                        PERIOD_END
                )).thenReturn(20L);
        lenient().when(attendanceRepository
                .countDistinctAttendanceDatesByStudentIdAndCohortIdAndDateBetweenAndStatusIn(
                        student.getStudentId(),
                        student.getCohortId(),
                        PERIOD_START,
                        PERIOD_END,
                        ABSENCE_STATUSES
                )).thenReturn(0L);
        lenient().when(stipendRepository.findByStudentIdAndStipendYearAndStipendMonth(
                student.getStudentId(), 2026, 5
        )).thenReturn(Optional.empty());
        lenient().when(policyRepository.findEffectivePolicyForCohort(
                student.getCohortId(), PERIOD_START
        )).thenReturn(Optional.of(policy));
        lenient().when(stipendRepository.save(any(MonthlyStipend.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void calculatesTierAdjustedPaymentForAbsences() {
        stubAbsences(3);

        MonthlyStipend stipend = calculateOne();

        assertEquals(3, stipend.getAbsenceCount());
        assertEquals(new BigDecimal("50.00"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("25000.00"), stipend.getCalculatedAmount());
        assertEquals(StipendStatus.CALCULATED, stipend.getStatus());
    }

    @Test
    void countsPresentDaysAndThenAppliesAttendancePolicy() {
        stubPresentDays(12);
        stubAbsences(3);

        MonthlyStipend stipend = calculateOne();

        assertEquals(3, stipend.getAbsenceCount());
        assertEquals(new BigDecimal("50.00"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("15000.00"), stipend.getCalculatedAmount());
    }

    @Test
    void startsCountingPresentDaysOnTheFirstPresentDateInclusive() {
        LocalDate activationDate = LocalDate.of(2026, 5, 15);
        stubFirstPresent(activationDate);
        stubPresentDays(6, activationDate, PERIOD_END);
        stubAbsences(0, activationDate, PERIOD_END);
        when(policyRepository.findEffectivePolicyForCohort(
                student.getCohortId(), activationDate
        )).thenReturn(Optional.of(policy));

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("15000.00"), stipend.getCalculatedAmount());
        verify(attendanceRepository)
                .countDistinctAttendanceDatesByStudentIdAndCohortIdAndStatusAndDateBetween(
                        student.getStudentId(),
                        student.getCohortId(),
                        AttendanceStatus.PRESENT,
                        activationDate,
                        PERIOD_END
                );
    }

    @Test
    void limitsMonthlyCohortClassDatesToInclusiveCohortBounds() {
        LocalDate cohortStart = LocalDate.of(2026, 5, 10);
        LocalDate cohortEnd = LocalDate.of(2026, 5, 20);
        student.setCohortStartDate(cohortStart);
        student.setCohortEndDate(cohortEnd);
        when(attendanceRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(), cohortStart, cohortEnd
        )).thenReturn(IntStream.rangeClosed(10, 20)
                .mapToObj(day -> LocalDate.of(2026, 5, day))
                .toList());
        stubFirstPresent(cohortStart, cohortStart, cohortEnd);
        stubPresentDays(11, cohortStart, cohortEnd);
        stubAbsences(0, cohortStart, cohortEnd);
        when(policyRepository.findEffectivePolicyForCohort(
                student.getCohortId(), cohortStart
        )).thenReturn(Optional.of(policy));

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("50000.00"), stipend.getCalculatedAmount());
        verify(attendanceRepository).findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(), cohortStart, cohortEnd
        );
    }

    @Test
    void skipsStudentsWithoutAnyPresentAttendanceInThePeriod() {
        when(attendanceRepository.findFirstAttendanceDateByStudentIdAndCohortIdAndStatusAndDateBetween(
                student.getStudentId(),
                student.getCohortId(),
                AttendanceStatus.PRESENT,
                student.getCohortStartDate(),
                PERIOD_END
        )).thenReturn(Optional.empty());

        assertTrue(service.calculateMonthlyStipend(PERIOD).isEmpty());
        verifyNoInteractions(stipendRepository, policyRepository);
    }

    @Test
    void skipsStudentsWhenNoCohortAttendanceDatesExistInTheMonth() {
        when(attendanceRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(List.of());

        assertTrue(service.calculateMonthlyStipend(PERIOD).isEmpty());
        verifyNoInteractions(stipendRepository, policyRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "PRESENT, false",
            "ABSENT, true",
            "LATE, true",
            "EXCUSED, false"
    })
    void appliesAbsencePolicyForEachAttendanceStatus(
            AttendanceStatus status,
            boolean expectedToCount) {
        stubAbsences(expectedToCount ? 1 : 0);

        MonthlyStipend stipend = calculateOne();

        assertEquals(expectedToCount ? 1 : 0, stipend.getAbsenceCount());
        assertEquals(expectedToCount, ABSENCE_STATUSES.contains(status));
        verify(attendanceRepository)
                .countDistinctAttendanceDatesByStudentIdAndCohortIdAndDateBetweenAndStatusIn(
                        eq(student.getStudentId()),
                        eq(student.getCohortId()),
                        eq(PERIOD_START),
                        eq(PERIOD_END),
                        eq(ABSENCE_STATUSES)
                );
    }

    @Test
    void roundsFinalAmountToTwoPlacesUsingHalfUp() {
        student.setStipendBaseAmount(new BigDecimal("10.01"));
        when(attendanceRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(List.of(PERIOD_START, PERIOD_START.plusDays(1)));
        stubPresentDays(1);
        stubAbsences(0);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("5.01"), stipend.getCalculatedAmount());
    }

    @Test
    void leavesApprovedStipendUnchangedOnRecalculation() {
        MonthlyStipend approved = MonthlyStipend.builder()
                .status(StipendStatus.APPROVED)
                .calculatedAmount(new BigDecimal("123.45"))
                .build();
        when(stipendRepository.findByStudentIdAndStipendYearAndStipendMonth(
                student.getStudentId(), 2026, 5
        )).thenReturn(Optional.of(approved));

        MonthlyStipend result = calculateOne();

        assertSame(approved, result);
        assertEquals(new BigDecimal("123.45"), result.getCalculatedAmount());
    }

    @Test
    void recalculatesExistingMonthlyRecordInsteadOfCreatingAnother() {
        AtomicReference<MonthlyStipend> persistedStipend = new AtomicReference<>();
        when(stipendRepository.findByStudentIdAndStipendYearAndStipendMonth(
                student.getStudentId(), 2026, 5
        )).thenAnswer(invocation -> Optional.ofNullable(persistedStipend.get()));
        when(stipendRepository.save(any(MonthlyStipend.class)))
                .thenAnswer(invocation -> {
                    MonthlyStipend stipend = invocation.getArgument(0);
                    if (stipend.getId() == null) {
                        stipend.setId(UUID.randomUUID());
                    }
                    persistedStipend.set(stipend);
                    return stipend;
                });

        MonthlyStipend first = calculateOne();
        MonthlyStipend second = calculateOne();

        assertSame(first, second);
        assertEquals(2026, second.getStipendYear());
        assertEquals(5, second.getStipendMonth());
        verify(stipendRepository, org.mockito.Mockito.times(2))
                .findByStudentIdAndStipendYearAndStipendMonth(
                        student.getStudentId(), 2026, 5
                );
    }

    private MonthlyStipend calculateOne() {
        return service.calculateMonthlyStipend(PERIOD).get(0);
    }

    private void stubFirstPresent(LocalDate activationDate) {
        stubFirstPresent(activationDate, student.getCohortStartDate(), PERIOD_END);
    }

    private void stubFirstPresent(
            LocalDate activationDate,
            LocalDate startDate,
            LocalDate endDate) {
        when(attendanceRepository
                .findFirstAttendanceDateByStudentIdAndCohortIdAndStatusAndDateBetween(
                        student.getStudentId(),
                        student.getCohortId(),
                        AttendanceStatus.PRESENT,
                        startDate,
                        endDate
                )).thenReturn(Optional.of(activationDate));
    }

    private void stubPresentDays(long count) {
        stubPresentDays(count, PERIOD_START, PERIOD_END);
    }

    private void stubPresentDays(long count, LocalDate startDate, LocalDate endDate) {
        when(attendanceRepository
                .countDistinctAttendanceDatesByStudentIdAndCohortIdAndStatusAndDateBetween(
                        student.getStudentId(),
                        student.getCohortId(),
                        AttendanceStatus.PRESENT,
                        startDate,
                        endDate
                )).thenReturn(count);
    }

    private void stubAbsences(long count) {
        stubAbsences(count, PERIOD_START, PERIOD_END);
    }

    private void stubAbsences(long count, LocalDate startDate, LocalDate endDate) {
        when(attendanceRepository
                .countDistinctAttendanceDatesByStudentIdAndCohortIdAndDateBetweenAndStatusIn(
                        student.getStudentId(),
                        student.getCohortId(),
                        startDate,
                        endDate,
                        ABSENCE_STATUSES
                )).thenReturn(count);
    }

    private static StipendTier tier(int min, Integer max, String percentage) {
        return StipendTier.builder()
                .minimumAbsences(min)
                .maximumAbsences(max)
                .paymentPercentage(new BigDecimal(percentage))
                .build();
    }
}
