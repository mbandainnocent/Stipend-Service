package com.stipend.service.StipendRecords;

import com.stipend.service.Enum.AttendanceStatus;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Enum.StudentStatus;
import com.stipend.service.Model.AttendanceRecord;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Model.StipendPolicy;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.AttendanceRecordRepository;
import com.stipend.service.Repository.MonthlyStipendRepository;
import com.stipend.service.Repository.StipendPolicyRepository;
import com.stipend.service.Repository.StudentRegistrationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class MonthlyStipendService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");

    private final StudentRegistrationRepository studentRegistrationRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MonthlyStipendRepository monthlyStipendRepository;
    private final StipendPolicyRepository stipendPolicyRepository;
    private final ZoneId businessTimeZone;

    public MonthlyStipendService(
            StudentRegistrationRepository studentRegistrationRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            MonthlyStipendRepository monthlyStipendRepository,
            StipendPolicyRepository stipendPolicyRepository,
            @Value("${app.registration.business-time-zone:Africa/Kigali}") String businessTimeZone
    ) {
        this.studentRegistrationRepository = studentRegistrationRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.monthlyStipendRepository = monthlyStipendRepository;
        this.stipendPolicyRepository = stipendPolicyRepository;
        this.businessTimeZone = ZoneId.of(businessTimeZone);
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
        LocalDate enrollmentStart = student.getRegistrationDate() == null
                ? student.getCohortStartDate()
                : student.getRegistrationDate().atZone(businessTimeZone).toLocalDate();
        LocalDate attendanceStart = max(
                periodStart,
                max(student.getCohortStartDate(), enrollmentStart)
        );
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

        StipendPolicy policy = stipendPolicyRepository.findEffectivePolicyForCohort(
                        student.getCohortId(),
                        attendanceStart
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "No effective stipend policy for cohort " + student.getCohortId()
                                + " on " + attendanceStart
                ));
        policy.validate();

        List<AttendanceRecord> attendanceRecords =
                attendanceRecordRepository.findByStudentIdAndCohortIdAndDateBetween(
                        student.getStudentId(),
                        student.getCohortId(),
                        attendanceStart,
                        attendanceEnd
                );
        AttendanceSummary summary = summarizeAttendance(cohortClassDates, attendanceRecords);
        BigDecimal attendancePercentage = summary.eligibleTrainingDays() == 0
                ? BigDecimal.ZERO
                : summary.creditedAttendanceDays()
                        .multiply(ONE_HUNDRED)
                        .divide(
                                BigDecimal.valueOf(summary.eligibleTrainingDays()),
                                6,
                                RoundingMode.HALF_UP
                        );
        BigDecimal paymentPercentage =
                policy.getPaymentPercentageForAttendance(attendancePercentage);
        BigDecimal calculatedAmount = baseAmount
                .multiply(BigDecimal.valueOf(summary.eligibleTrainingDays()))
                .multiply(paymentPercentage)
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
        stipend.setAbsenceCount(summary.unauthorizedAbsenceDays());
        stipend.setScheduledTrainingDays(cohortClassDates.size());
        stipend.setEligibleTrainingDays(summary.eligibleTrainingDays());
        stipend.setCreditedAttendanceDays(summary.creditedAttendanceDays());
        stipend.setAttendancePercentage(attendancePercentage);
        stipend.setUnauthorizedAbsenceDays(summary.unauthorizedAbsenceDays());
        stipend.setBaseAmount(baseAmount);
        stipend.setPaymentPercentage(paymentPercentage);
        stipend.setCalculatedAmount(calculatedAmount);
        stipend.setCurrency(student.getStipendCurrency());
        stipend.setStatus(StipendStatus.CALCULATED);
        stipend.setCalculatedAt(now);
        stipend.setUpdatedAt(now);
        return Optional.of(monthlyStipendRepository.save(stipend));
    }

    private AttendanceSummary summarizeAttendance(
            List<LocalDate> cohortClassDates,
            List<AttendanceRecord> attendanceRecords
    ) {
        Comparator<AttendanceRecord> newestRecordFirst = Comparator
                .comparing(
                        AttendanceRecord::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                )
                .thenComparingInt(AttendanceRecord::getEventVersion);
        Map<LocalDate, AttendanceRecord> dailyAttendance = new HashMap<>();
        for (AttendanceRecord record : attendanceRecords) {
            dailyAttendance.merge(record.getDate(), record, (existing, incoming) ->
                    newestRecordFirst.compare(existing, incoming) <= 0 ? incoming : existing);
        }

        int eligibleTrainingDays = cohortClassDates.size();
        BigDecimal creditedAttendanceDays = BigDecimal.ZERO;
        BigDecimal unauthorizedAbsenceDays = BigDecimal.ZERO;

        for (LocalDate classDate : cohortClassDates) {
            AttendanceRecord record = dailyAttendance.get(classDate);
            if (record == null) {
                unauthorizedAbsenceDays = unauthorizedAbsenceDays.add(BigDecimal.ONE);
                continue;
            }

            AttendanceStatus status = record.getStatus();
            if (status == null) {
                throw new IllegalStateException(
                        "Attendance record has no status for date " + classDate
                );
            }

            switch (status) {
                case PRESENT -> creditedAttendanceDays =
                        creditedAttendanceDays.add(BigDecimal.ONE);
                case LATE -> {
                    creditedAttendanceDays = creditedAttendanceDays.add(HALF_DAY);
                    unauthorizedAbsenceDays = unauthorizedAbsenceDays.add(HALF_DAY);
                }
                case ABSENT -> {
                    unauthorizedAbsenceDays = unauthorizedAbsenceDays.add(BigDecimal.ONE);
                }
                case EXCUSED -> eligibleTrainingDays--;
                case APPROVED_HALF_DAY ->
                        creditedAttendanceDays = creditedAttendanceDays.add(HALF_DAY);
            }
        }

        return new AttendanceSummary(
                eligibleTrainingDays,
                creditedAttendanceDays,
                unauthorizedAbsenceDays
        );
    }

    private boolean overlapsPeriod(Student student, YearMonth period) {
        LocalDate enrollmentStart = student.getRegistrationDate() == null
                ? student.getCohortStartDate()
                : student.getRegistrationDate().atZone(businessTimeZone).toLocalDate();
        return !max(student.getCohortStartDate(), enrollmentStart).isAfter(period.atEndOfMonth())
                && !student.getCohortEndDate().isBefore(period.atDay(1));
    }

    private LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private record AttendanceSummary(
            int eligibleTrainingDays,
            BigDecimal creditedAttendanceDays,
            BigDecimal unauthorizedAbsenceDays
    ) {
    }
}
