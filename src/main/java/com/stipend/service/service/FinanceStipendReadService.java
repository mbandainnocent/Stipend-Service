package com.stipend.service.service;

import com.stipend.service.DTO.MonthlyStipendPageDTO;
import com.stipend.service.DTO.MonthlyStipendResponseDTO;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Model.MonthlyStipendFinanceView;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.MonthlyStipendRepository;
import com.stipend.service.Repository.StudentRegistrationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FinanceStipendReadService {

    private final MonthlyStipendRepository monthlyStipendRepository;
    private final StudentRegistrationRepository studentRepository;

    public FinanceStipendReadService(
            MonthlyStipendRepository monthlyStipendRepository,
            StudentRegistrationRepository studentRepository
    ) {
        this.monthlyStipendRepository = monthlyStipendRepository;
        this.studentRepository = studentRepository;
    }

    public MonthlyStipendPageDTO searchStipends(
            Integer year,
            Integer month,
            UUID cohortId,
            UUID studentId,
            StipendStatus status,
            Pageable pageable
    ) {
        Page<MonthlyStipendFinanceView> result = monthlyStipendRepository.searchFinanceView(
                year,
                month,
                cohortId,
                studentId,
                status,
                pageable
        );
        return new MonthlyStipendPageDTO(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    public List<MonthlyStipendResponseDTO> getStipendsForPeriod(YearMonth period) {
        return monthlyStipendRepository.searchFinanceView(
                        period.getYear(),
                        period.getMonthValue(),
                        null,
                        null,
                        null,
                        Pageable.unpaged()
                )
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<MonthlyStipendResponseDTO> getStudentStipendForPeriod(UUID studentId, YearMonth period) {
        return monthlyStipendRepository.searchFinanceView(
                        period.getYear(),
                        period.getMonthValue(),
                        null,
                        studentId,
                        null,
                        Pageable.unpaged()
                )
                .getContent()
                .stream()
                .findFirst()
                .map(this::toResponse);
    }

    public MonthlyStipendResponseDTO getStipend(UUID stipendId) {
        MonthlyStipend stipend = monthlyStipendRepository.findById(stipendId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Monthly stipend not found: " + stipendId
                ));
        Student student = studentRepository.findById(stipend.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found for monthly stipend: " + stipendId
                ));
        return toResponse(stipend, student);
    }

    public List<MonthlyStipendResponseDTO> getStudentHistory(UUID studentId, Integer year) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found: " + studentId);
        }
        Page<MonthlyStipendFinanceView> result = monthlyStipendRepository.searchFinanceView(
                year,
                null,
                null,
                studentId,
                null,
                Pageable.unpaged()
        );
        return result.getContent().stream().map(this::toResponse).toList();
    }

    public List<MonthlyStipendResponseDTO> getCohortStipends(
            UUID cohortId,
            int year,
            int month,
            StipendStatus status
    ) {
        return monthlyStipendRepository.searchFinanceView(
                        year,
                        month,
                        cohortId,
                        null,
                        status,
                        Pageable.unpaged()
                )
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private MonthlyStipendResponseDTO toResponse(MonthlyStipendFinanceView view) {
        return new MonthlyStipendResponseDTO(
                view.stipendId(),
                view.studentId(),
                view.firstName(),
                view.lastName(),
                view.cohortId(),
                view.cohortStartDate(),
                view.cohortEndDate(),
                view.policyId(),
                view.stipendYear(),
                view.stipendMonth(),
                view.stipendPeriodStartDate(),
                view.stipendPeriodEndDate(),
                view.absenceCount(),
                view.scheduledTrainingDays(),
                view.eligibleTrainingDays(),
                view.creditedAttendanceDays(),
                view.attendancePercentage(),
                view.unauthorizedAbsenceDays(),
                view.baseAmount(),
                view.paymentPercentage(),
                view.calculatedAmount(),
                view.currency(),
                view.status(),
                view.approvedBy(),
                view.approvalComment(),
                view.approvedAt(),
                view.paymentReference(),
                view.paidAt(),
                view.calculatedAt(),
                view.updatedAt()
        );
    }

    private MonthlyStipendResponseDTO toResponse(MonthlyStipend stipend, Student student) {
        YearMonth period = YearMonth.of(stipend.getStipendYear(), stipend.getStipendMonth());
        return new MonthlyStipendResponseDTO(
                stipend.getId(),
                stipend.getStudentId(),
                student.getFirstName(),
                student.getLastName(),
                student.getCohortId(),
                student.getCohortStartDate(),
                student.getCohortEndDate(),
                stipend.getPolicyId(),
                stipend.getStipendYear(),
                stipend.getStipendMonth(),
                period.atDay(1),
                period.atEndOfMonth(),
                stipend.getAbsenceCount(),
                stipend.getScheduledTrainingDays(),
                stipend.getEligibleTrainingDays(),
                stipend.getCreditedAttendanceDays(),
                stipend.getAttendancePercentage(),
                stipend.getUnauthorizedAbsenceDays(),
                stipend.getBaseAmount(),
                stipend.getPaymentPercentage(),
                stipend.getCalculatedAmount(),
                stipend.getCurrency(),
                stipend.getStatus(),
                stipend.getApprovedBy(),
                stipend.getApprovalComment(),
                stipend.getApprovedAt(),
                stipend.getPaymentReference(),
                stipend.getPaidAt(),
                stipend.getCalculatedAt(),
                stipend.getUpdatedAt()
        );
    }
}
