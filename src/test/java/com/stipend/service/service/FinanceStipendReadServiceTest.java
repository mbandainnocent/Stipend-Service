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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceStipendReadServiceTest {

    @Mock
    private MonthlyStipendRepository monthlyStipendRepository;

    @Mock
    private StudentRegistrationRepository studentRepository;

    @InjectMocks
    private FinanceStipendReadService service;

    @Test
    void searchStipendsReturnsPaginatedFinanceDtosAndFilters() {
        UUID cohortId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        var pageable = PageRequest.of(1, 5, Sort.by("stipendYear").descending());
        var view = financeView(2024, 2, StipendStatus.CALCULATED);
        when(monthlyStipendRepository.searchFinanceView(
                2024, 2, cohortId, studentId, StipendStatus.CALCULATED, pageable
        )).thenReturn(new PageImpl<>(List.of(view), pageable, 11));

        MonthlyStipendPageDTO result = service.searchStipends(
                2024, 2, cohortId, studentId, StipendStatus.CALCULATED, pageable
        );

        assertEquals(1, result.page());
        assertEquals(5, result.size());
        assertEquals(11, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(List.of(toExpectedFebruaryDto(view)), result.content());
        verify(monthlyStipendRepository).searchFinanceView(
                2024, 2, cohortId, studentId, StipendStatus.CALCULATED, pageable
        );
    }

    @Test
    void getStipendMapsNamesPeriodAndFinalAmount() {
        UUID stipendId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        MonthlyStipend stipend = MonthlyStipend.builder()
                .id(stipendId)
                .studentId(studentId)
                .cohortId(cohortId)
                .policyId(UUID.randomUUID())
                .stipendYear(2024)
                .stipendMonth(2)
                .absenceCount(1)
                .baseAmount(new BigDecimal("40000.00"))
                .paymentPercentage(new BigDecimal("75.00"))
                .calculatedAmount(new BigDecimal("25000.00"))
                .currency("RWF")
                .status(StipendStatus.CALCULATED)
                .calculatedAt(Instant.parse("2024-03-01T00:00:00Z"))
                .updatedAt(Instant.parse("2024-03-01T00:00:00Z"))
                .build();
        Student student = Student.builder()
                .studentId(studentId)
                .firstName("Aline")
                .lastName("Uwase")
                .cohortId(cohortId)
                .cohortStartDate(LocalDate.of(2024, 1, 10))
                .cohortEndDate(LocalDate.of(2024, 12, 31))
                .build();
        when(monthlyStipendRepository.findById(stipendId)).thenReturn(Optional.of(stipend));
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));

        MonthlyStipendResponseDTO result = service.getStipend(stipendId);

        assertEquals("Aline", result.firstName());
        assertEquals("Uwase", result.lastName());
        assertEquals(cohortId, result.cohortId());
        assertEquals(LocalDate.of(2024, 2, 1), result.stipendPeriodStartDate());
        assertEquals(LocalDate.of(2024, 2, 29), result.stipendPeriodEndDate());
        assertEquals(new BigDecimal("25000.00"), result.calculatedAmount());
        assertEquals("RWF", result.currency());
    }

    @Test
    void getStipendWhenMissingThrowsNotFound() {
        UUID stipendId = UUID.randomUUID();
        when(monthlyStipendRepository.findById(stipendId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getStipend(stipendId));
    }

    @Test
    void getStudentStipendForPeriodFiltersByStudentAndPeriod() {
        UUID studentId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2025, 6);
        MonthlyStipendFinanceView view = financeView(2025, 6, StipendStatus.CALCULATED);
        when(monthlyStipendRepository.searchFinanceView(
                eq(period.getYear()), eq(period.getMonthValue()), eq(null), eq(studentId), eq(null), any()
        )).thenReturn(new PageImpl<>(List.of(view)));

        Optional<MonthlyStipendResponseDTO> result = service.getStudentStipendForPeriod(studentId, period);

        assertEquals(view.stipendId(), result.orElseThrow().id());
        assertEquals(view.studentId(), result.orElseThrow().studentId());
        verify(monthlyStipendRepository).searchFinanceView(
                eq(period.getYear()), eq(period.getMonthValue()), eq(null), eq(studentId), eq(null), any()
        );
    }

    @Test
    void getStudentHistoryFiltersByYearAndReturnsRows() {
        UUID studentId = UUID.randomUUID();
        when(studentRepository.existsById(studentId)).thenReturn(true);
        when(monthlyStipendRepository.searchFinanceView(
                eq(2025), eq(null), eq(null), eq(studentId), eq(null), any()
        )).thenReturn(new PageImpl<>(List.of(financeView(2025, 12, StipendStatus.PAID))));

        List<MonthlyStipendResponseDTO> result = service.getStudentHistory(studentId, 2025);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2025, 12, 31), result.get(0).stipendPeriodEndDate());
        verify(monthlyStipendRepository).searchFinanceView(
                eq(2025), eq(null), eq(null), eq(studentId), eq(null), any()
        );
    }

    @Test
    void getStudentHistoryForMissingStudentThrowsNotFound() {
        UUID studentId = UUID.randomUUID();
        when(studentRepository.existsById(studentId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.getStudentHistory(studentId, null));
    }

    @Test
    void getCohortStipendsFiltersByCohortPeriodAndStatus() {
        UUID cohortId = UUID.randomUUID();
        when(monthlyStipendRepository.searchFinanceView(
                eq(2025), eq(6), eq(cohortId), eq(null), eq(StipendStatus.APPROVED), any()
        )).thenReturn(new PageImpl<>(List.of(financeView(2025, 6, StipendStatus.APPROVED))));

        List<MonthlyStipendResponseDTO> result = service.getCohortStipends(
                cohortId, 2025, 6, StipendStatus.APPROVED
        );

        assertEquals(1, result.size());
        assertEquals(StipendStatus.APPROVED, result.get(0).status());
        verify(monthlyStipendRepository).searchFinanceView(
                eq(2025), eq(6), eq(cohortId), eq(null), eq(StipendStatus.APPROVED), any()
        );
    }

    private MonthlyStipendFinanceView financeView(int year, int month, StipendStatus status) {
        return new MonthlyStipendFinanceView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Aline",
                "Uwase",
                UUID.randomUUID(),
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31),
                UUID.randomUUID(),
                year,
                month,
                1,
                new BigDecimal("40000.00"),
                new BigDecimal("75.00"),
                new BigDecimal("25000.00"),
                "RWF",
                status,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z")
        );
    }

    private MonthlyStipendResponseDTO toExpectedFebruaryDto(MonthlyStipendFinanceView view) {
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
                LocalDate.of(view.stipendYear(), view.stipendMonth(), 1),
                LocalDate.of(view.stipendYear(), view.stipendMonth(), 29),
                view.absenceCount(),
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
}
