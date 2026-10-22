package com.stipend.service.StipendRecords;

import com.stipend.service.Enum.AttendanceStatus;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Enum.StudentStatus;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Model.StipendPolicy;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.AttendanceRecordRepository;
import com.stipend.service.Repository.MonthlyStipendRepository;
import com.stipend.service.Repository.StudentRegistrationRepository;
import com.stipend.service.Repository.StipendPolicyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class MonthlyStipendService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final StudentRegistrationRepository studentRegistrationRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MonthlyStipendRepository monthlyStipendRepository;
    private final StipendPolicyRepository stipendPolicyRepository;

    public MonthlyStipendService(
            StudentRegistrationRepository studentRegistrationRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            MonthlyStipendRepository monthlyStipendRepository,
            StipendPolicyRepository stipendPolicyRepository
    ) {
        this.studentRegistrationRepository = studentRegistrationRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.monthlyStipendRepository = monthlyStipendRepository;
        this.stipendPolicyRepository = stipendPolicyRepository;
    }

    @Transactional
    public List<MonthlyStipend> calculateMonthlyStipend(YearMonth period) {
        Objects.requireNonNull(period, "period must not be null");
        List<Student> eligibleStudents = studentRegistrationRepository
                .findByStatusAndStipendEligibleTrue(StudentStatus.ACTIVE);

        return eligibleStudents.stream()
                .filter(student -> overlapsPeriod(student, period))
                .map(student -> calculateForStudent(student, period))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<MonthlyStipend> calculateForStudent(Student student, YearMonth period) {
        if (student.getStatus() != StudentStatus.ACTIVE || !student.isStipendEligible()) {
            throw new IllegalArgumentException(
                    "Student is not currently eligible for a stipend: " + student.getStudentId()
            );
        }

        BigDecimal baseAmount = student.getStipendBaseAmount();
        if (baseAmount == null || student.getStipendCurrency() == null
                || student.getStipendCurrency().isBlank()) {
            throw new IllegalStateException(
                    "Eligible student is missing stipend amount or currency: "
                            + student.getStudentId()
            );
        }

        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();
        LocalDate attendanceStart = max(periodStart, student.getCohortStartDate());
        LocalDate attendanceEnd = min(periodEnd, student.getCohortEndDate());
        List<LocalDate> cohortClassDates =
                attendanceRecordRepository.findDistinctAttendanceDatesByCohortIdAndDateBetween(
                        student.getCohortId(),
                        attendanceStart,
                        attendanceEnd
                );
        if (cohortClassDates.isEmpty()) {
            return Optional.empty();
        }

        Optional<LocalDate> firstPresentDate =
                attendanceRecordRepository.findFirstAttendanceDateByStudentIdAndCohortIdAndStatusAndDateBetween(
                        student.getStudentId(),
                        student.getCohortId(),
                        AttendanceStatus.PRESENT,
                        student.getCohortStartDate(),
                        min(student.getCohortEndDate(), periodEnd)
                );
        if (firstPresentDate.isEmpty()) {
            return Optional.empty();
        }

        LocalDate calculationStart = max(attendanceStart, firstPresentDate.get());

        long presentDays =
                attendanceRecordRepository.countDistinctAttendanceDatesByStudentIdAndCohortIdAndStatusAndDateBetween(
                        student.getStudentId(),
                        student.getCohortId(),
                        AttendanceStatus.PRESENT,
                        calculationStart,
                        attendanceEnd
                );
        long absences =
                attendanceRecordRepository.countDistinctAttendanceDatesByStudentIdAndCohortIdAndDateBetweenAndStatusIn(
                        student.getStudentId(),
                        student.getCohortId(),
                        calculationStart,
                        attendanceEnd,
                        Set.of(AttendanceStatus.ABSENT, AttendanceStatus.LATE)
                );

        StipendPolicy policy = stipendPolicyRepository.findEffectivePolicyForCohort(
                        student.getCohortId(),
                        calculationStart
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "No effective stipend policy for cohort " + student.getCohortId()
                                + " on " + calculationStart
                ));
        BigDecimal percentage = policy.getPaymentPercentageForAbsences(Math.toIntExact(absences));
        BigDecimal calculatedAmount = baseAmount
                .multiply(BigDecimal.valueOf(presentDays))
                .multiply(percentage)
                .divide(
                        BigDecimal.valueOf(cohortClassDates.size()).multiply(ONE_HUNDRED),
                        2,
                        RoundingMode.HALF_UP
                );

        MonthlyStipend stipend = monthlyStipendRepository
                .findByStudentIdAndStipendYearAndStipendMonth(
                        student.getStudentId(),
                        period.getYear(),
                        period.getMonthValue()
                )
                .orElseGet(MonthlyStipend::new);

        if (stipend.getStatus() == StipendStatus.APPROVED
                || stipend.getStatus() == StipendStatus.PAID
                || stipend.getStatus() == StipendStatus.CANCELLED) {
            return Optional.of(stipend);
        }

        Instant now = Instant.now();
        stipend.setStudentId(student.getStudentId());
        stipend.setCohortId(student.getCohortId());
        stipend.setPolicyId(policy.getPolicyId());
        stipend.setStipendYear(period.getYear());
        stipend.setStipendMonth(period.getMonthValue());
        stipend.setAbsenceCount(Math.toIntExact(absences));
        stipend.setBaseAmount(baseAmount);
        stipend.setPaymentPercentage(percentage);
        stipend.setCalculatedAmount(calculatedAmount);
        stipend.setCurrency(student.getStipendCurrency());
        stipend.setStatus(StipendStatus.CALCULATED);
        stipend.setCalculatedAt(now);
        stipend.setUpdatedAt(now);
        return Optional.of(monthlyStipendRepository.save(stipend));
    }

    private boolean overlapsPeriod(Student student, YearMonth period) {
        return !student.getCohortStartDate().isAfter(period.atEndOfMonth())
                && !student.getCohortEndDate().isBefore(period.atDay(1));
    }

    private LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
