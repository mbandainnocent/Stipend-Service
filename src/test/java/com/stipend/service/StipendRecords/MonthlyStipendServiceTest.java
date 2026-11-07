package com.stipend.service.StipendRecords;

import com.stipend.service.Enum.AttendanceStatus;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Enum.StudentStatus;
import com.stipend.service.Model.AttendanceRecord;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyStipendServiceTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 5);
    private static final LocalDate PERIOD_START = PERIOD.atDay(1);
    private static final LocalDate PERIOD_END = PERIOD.atEndOfMonth();

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
    private List<LocalDate> classDates;

    @BeforeEach
    void setUp() {
        service = new MonthlyStipendService(
                studentRepository,
                attendanceRepository,
                stipendRepository,
                policyRepository,
                "Africa/Kigali"
        );
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
        policy = StipendPolicy.builder()
                .policyId(UUID.randomUUID())
                .cohortId(student.getCohortId())
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .tiers(List.of(
                        tier("0", "50"),
                        tier("85", "75"),
                        tier("90", "90"),
                        tier("95", "100")
                ))
                .build();
        classDates = IntStream.rangeClosed(1, 20)
                .mapToObj(day -> LocalDate.of(2026, 5, day))
                .toList();

        lenient().when(studentRepository.findByStatusAndStipendEligibleTrue(StudentStatus.ACTIVE))
                .thenReturn(List.of(student));
        lenient().when(attendanceRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(classDates);
        lenient().when(policyRepository.findEffectivePolicyForCohort(
                student.getCohortId(), PERIOD_START
        )).thenReturn(Optional.of(policy));
        lenient().when(attendanceRepository.findByStudentIdAndCohortIdAndDateBetween(
                student.getStudentId(), student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(recordsFor(List.of(AttendanceStatus.PRESENT), 20));
        lenient().when(stipendRepository.findByStudentIdAndStipendYearAndStipendMonth(
                student.getStudentId(), 2026, 5
        )).thenReturn(Optional.empty());
        lenient().when(stipendRepository.save(any(MonthlyStipend.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void paysFullBaseAmountAtNinetyFivePercentAttendance() {
        setStatuses(19, AttendanceStatus.PRESENT, 1, AttendanceStatus.ABSENT);

        MonthlyStipend stipend = calculateOne();

        assertEquals(20, stipend.getScheduledTrainingDays());
        assertEquals(20, stipend.getEligibleTrainingDays());
        assertEquals(new BigDecimal("19"), stipend.getCreditedAttendanceDays());
        assertEquals(new BigDecimal("95.000000"), stipend.getAttendancePercentage());
        assertEquals(new BigDecimal("100"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("50000.00"), stipend.getCalculatedAmount());
    }

    @Test
    void appliesNinetyPercentTierAtNinetyPercentAttendance() {
        setStatuses(18, AttendanceStatus.PRESENT, 2, AttendanceStatus.ABSENT);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("90"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("45000.00"), stipend.getCalculatedAmount());
    }

    @Test
    void appliesNinetyPercentTierToOneAbsenceInAnEighteenDayMonth() {
        classDates = IntStream.rangeClosed(1, 18)
                .mapToObj(day -> LocalDate.of(2026, 5, day))
                .toList();
        when(attendanceRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(classDates);
        setStatuses(17, AttendanceStatus.PRESENT, 1, AttendanceStatus.ABSENT);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("94.444444"), stipend.getAttendancePercentage());
        assertEquals(new BigDecimal("90"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("45000.00"), stipend.getCalculatedAmount());
    }

    @Test
    void appliesSeventyFivePercentTierAtEightyFivePercentAttendance() {
        setStatuses(17, AttendanceStatus.PRESENT, 3, AttendanceStatus.ABSENT);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("75"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("37500.00"), stipend.getCalculatedAmount());
    }

    @Test
    void appliesFiftyPercentTierBelowEightyFivePercentAttendance() {
        setStatuses(16, AttendanceStatus.PRESENT, 4, AttendanceStatus.ABSENT);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("50"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("25000.00"), stipend.getCalculatedAmount());
        assertEquals(new BigDecimal("4"), stipend.getAbsenceCount());
        assertEquals(new BigDecimal("4"), stipend.getUnauthorizedAbsenceDays());
    }

    @Test
    void excludesApprovedFullAbsencesFromAttendanceAndProRatesEligibleDays() {
        setStatuses(15, AttendanceStatus.PRESENT, 5, AttendanceStatus.EXCUSED);

        MonthlyStipend stipend = calculateOne();

        assertEquals(20, stipend.getScheduledTrainingDays());
        assertEquals(15, stipend.getEligibleTrainingDays());
        assertEquals(new BigDecimal("100.000000"), stipend.getAttendancePercentage());
        assertEquals(new BigDecimal("100"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("37500.00"), stipend.getCalculatedAmount());
        assertEquals(BigDecimal.ZERO, stipend.getUnauthorizedAbsenceDays());
    }

    @Test
    void lateArrivalCountsAsHalfDayOfAttendanceAndUnauthorizedAbsence() {
        setStatuses(18, AttendanceStatus.PRESENT, 1, AttendanceStatus.LATE, 1, AttendanceStatus.ABSENT);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("18.5"), stipend.getCreditedAttendanceDays());
        assertEquals(new BigDecimal("92.500000"), stipend.getAttendancePercentage());
        assertEquals(new BigDecimal("1.5"), stipend.getUnauthorizedAbsenceDays());
        assertEquals(new BigDecimal("1.5"), stipend.getAbsenceCount());
        assertEquals(new BigDecimal("90"), stipend.getPaymentPercentage());
        assertEquals(new BigDecimal("45000.00"), stipend.getCalculatedAmount());
    }

    @Test
    void approvedHalfDayCreditsHalfAttendanceWithoutUnauthorizedAbsence() {
        setStatuses(19, AttendanceStatus.PRESENT, 1, AttendanceStatus.APPROVED_HALF_DAY);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("19.5"), stipend.getCreditedAttendanceDays());
        assertEquals(new BigDecimal("97.500000"), stipend.getAttendancePercentage());
        assertEquals(BigDecimal.ZERO, stipend.getUnauthorizedAbsenceDays());
        assertEquals(new BigDecimal("100"), stipend.getPaymentPercentage());
    }

    @Test
    void missingParticipantAttendanceIsAnUnauthorizedFullDayAbsence() {
        setStatuses(19, AttendanceStatus.PRESENT);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("1"), stipend.getAbsenceCount());
        assertEquals(new BigDecimal("1"), stipend.getUnauthorizedAbsenceDays());
        assertEquals(new BigDecimal("95.000000"), stipend.getAttendancePercentage());
    }

    @Test
    void considersScheduledDaysBeforeFirstPresentRecord() {
        List<AttendanceRecord> records = recordsFor(List.of(AttendanceStatus.ABSENT), 1);
        records.addAll(recordsFor(List.of(AttendanceStatus.PRESENT), 19, 2));
        when(attendanceRepository.findByStudentIdAndCohortIdAndDateBetween(
                student.getStudentId(), student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(records);

        MonthlyStipend stipend = calculateOne();

        assertEquals(new BigDecimal("1"), stipend.getAbsenceCount());
        assertEquals(new BigDecimal("95.000000"), stipend.getAttendancePercentage());
    }

    @Test
    void startsScheduledDaysOnParticipantRegistrationDate() {
        student.setRegistrationDate(java.time.Instant.parse("2026-05-10T22:00:00Z"));
        List<LocalDate> enrolledClassDates = IntStream.rangeClosed(11, 20)
                .mapToObj(day -> LocalDate.of(2026, 5, day))
                .toList();
        when(attendanceRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(),
                LocalDate.of(2026, 5, 11),
                PERIOD_END
        )).thenReturn(enrolledClassDates);
        when(policyRepository.findEffectivePolicyForCohort(
                student.getCohortId(), LocalDate.of(2026, 5, 11)
        )).thenReturn(Optional.of(policy));
        when(attendanceRepository.findByStudentIdAndCohortIdAndDateBetween(
                student.getStudentId(),
                student.getCohortId(),
                LocalDate.of(2026, 5, 11),
                PERIOD_END
        )).thenReturn(recordsFor(List.of(AttendanceStatus.PRESENT), 10, 11));

        MonthlyStipend stipend = calculateOne();

        assertEquals(10, stipend.getScheduledTrainingDays());
        assertEquals(10, stipend.getEligibleTrainingDays());
        assertEquals(BigDecimal.ZERO, stipend.getAbsenceCount());
        assertEquals(new BigDecimal("100.000000"), stipend.getAttendancePercentage());
    }

    @Test
    void skipsCalculationWhenCohortHasNoObservedTrainingDays() {
        when(attendanceRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(List.of());

        assertTrue(service.calculateMonthlyStipend(PERIOD).isEmpty());
        verifyNoInteractions(stipendRepository, policyRepository);
    }

    private MonthlyStipend calculateOne() {
        return service.calculateMonthlyStipend(PERIOD).get(0);
    }

    private void setStatuses(Object... countsAndStatuses) {
        List<AttendanceRecord> records = new ArrayList<>();
        int dayOffset = 0;
        for (int index = 0; index < countsAndStatuses.length; index += 2) {
            int count = (int) countsAndStatuses[index];
            AttendanceStatus status = (AttendanceStatus) countsAndStatuses[index + 1];
            records.addAll(recordsFor(List.of(status), count, dayOffset + 1));
            dayOffset += count;
        }
        when(attendanceRepository.findByStudentIdAndCohortIdAndDateBetween(
                student.getStudentId(), student.getCohortId(), PERIOD_START, PERIOD_END
        )).thenReturn(records);
    }

    private List<AttendanceRecord> recordsFor(List<AttendanceStatus> statuses, int count) {
        return recordsFor(statuses, count, 1);
    }

    private List<AttendanceRecord> recordsFor(
            List<AttendanceStatus> statuses,
            int count,
            int startDay
    ) {
        List<AttendanceRecord> records = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            AttendanceStatus status = statuses.get(index % statuses.size());
            records.add(AttendanceRecord.builder()
                    .attendanceId(UUID.randomUUID())
                    .studentId(student.getStudentId())
                    .cohortId(student.getCohortId())
                    .date(LocalDate.of(2026, 5, startDay + index))
                    .status(status)
                    .eventVersion(1)
                    .build());
        }
        return records;
    }

    private StipendTier tier(String minimumAttendance, String percentage) {
        return StipendTier.builder()
                .minimumAttendancePercentage(new BigDecimal(minimumAttendance))
                .paymentPercentage(new BigDecimal(percentage))
                .build();
    }
}
