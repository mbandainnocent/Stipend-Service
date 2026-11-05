package com.stipend.service.service;

import com.stipend.service.DTO.ApproveStipendRequestDTO;
import com.stipend.service.DTO.MarkPaidStipendRequestDTO;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Exception.FinancePrincipalRequiredException;
import com.stipend.service.Exception.InvalidStipendTransitionException;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Repository.MonthlyStipendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FinanceStipendWorkflowServiceTest {

    private MonthlyStipendRepository stipendRepository;
    private FinanceStipendReadService readService;
    private FinanceStipendWorkflowService service;

    @BeforeEach
    void setUp() {
        stipendRepository = mock(MonthlyStipendRepository.class);
        readService = mock(FinanceStipendReadService.class);
        service = new FinanceStipendWorkflowService(stipendRepository, readService);
    }

    @Test
    void approvesCalculatedStipendAndRecordsApproverAndComment() {
        UUID stipendId = UUID.randomUUID();
        MonthlyStipend stipend = stipend(stipendId, StipendStatus.CALCULATED);
        when(stipendRepository.findByIdForUpdate(stipendId)).thenReturn(Optional.of(stipend));

        service.approve(stipendId, "finance.user", new ApproveStipendRequestDTO("Reviewed"));

        assertThat(stipend.getStatus()).isEqualTo(StipendStatus.APPROVED);
        assertThat(stipend.getApprovedBy()).isEqualTo("finance.user");
        assertThat(stipend.getApprovalComment()).isEqualTo("Reviewed");
        assertThat(stipend.getApprovedAt()).isNotNull();
        assertThat(stipend.getUpdatedAt()).isNotNull();
        verify(stipendRepository).save(stipend);
        verify(readService).getStipend(stipendId);
    }

    @Test
    void marksApprovedStipendPaidAndRecordsReferenceAndTime() {
        UUID stipendId = UUID.randomUUID();
        MonthlyStipend stipend = stipend(stipendId, StipendStatus.APPROVED);
        Instant paidAt = Instant.parse("2026-09-05T14:30:00Z");
        when(stipendRepository.findByIdForUpdate(stipendId)).thenReturn(Optional.of(stipend));

        service.markPaid(
                stipendId,
                "finance.user",
                new MarkPaidStipendRequestDTO("MTN-2026-08-000123", paidAt)
        );

        assertThat(stipend.getStatus()).isEqualTo(StipendStatus.PAID);
        assertThat(stipend.getPaymentReference()).isEqualTo("MTN-2026-08-000123");
        assertThat(stipend.getPaidAt()).isEqualTo(paidAt);
        assertThat(stipend.getUpdatedAt()).isNotNull();
        verify(stipendRepository).save(stipend);
        verify(readService).getStipend(stipendId);
    }

    @Test
    void rejectsInvalidAndRepeatedTransitions() {
        UUID stipendId = UUID.randomUUID();
        MonthlyStipend stipend = stipend(stipendId, StipendStatus.CALCULATED);
        when(stipendRepository.findByIdForUpdate(stipendId)).thenReturn(Optional.of(stipend));

        assertThatThrownBy(() -> service.markPaid(
                stipendId,
                "finance.user",
                new MarkPaidStipendRequestDTO("payment-123", Instant.now())
        )).isInstanceOf(InvalidStipendTransitionException.class);

        service.approve(stipendId, "finance.user", null);
        assertThatThrownBy(() -> service.approve(stipendId, "finance.user", null))
                .isInstanceOf(InvalidStipendTransitionException.class);

        verify(stipendRepository, times(1)).save(stipend);
    }

    @Test
    void rejectsRepeatedPaymentAfterTransitionToPaid() {
        UUID stipendId = UUID.randomUUID();
        MonthlyStipend stipend = stipend(stipendId, StipendStatus.APPROVED);
        when(stipendRepository.findByIdForUpdate(stipendId)).thenReturn(Optional.of(stipend));
        MarkPaidStipendRequestDTO request =
                new MarkPaidStipendRequestDTO("payment-123", Instant.now());

        service.markPaid(stipendId, "finance.user", request);

        assertThatThrownBy(() -> service.markPaid(stipendId, "finance.user", request))
                .isInstanceOf(InvalidStipendTransitionException.class);
        verify(stipendRepository, times(1)).save(stipend);
    }

    @Test
    void validatesApproverAndRequestFields() {
        UUID stipendId = UUID.randomUUID();

        assertThatThrownBy(() -> service.approve(stipendId, " ", null))
                .isInstanceOf(FinancePrincipalRequiredException.class);
        assertThatThrownBy(() -> service.approve(
                stipendId,
                "finance.user",
                new ApproveStipendRequestDTO("x".repeat(501))
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markPaid(stipendId, "finance.user", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markPaid(
                stipendId,
                "finance.user",
                new MarkPaidStipendRequestDTO(" ", Instant.now())
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markPaid(
                stipendId,
                "finance.user",
                new MarkPaidStipendRequestDTO("x".repeat(101), Instant.now())
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markPaid(
                stipendId,
                "finance.user",
                new MarkPaidStipendRequestDTO("payment-123", null)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markPaid(
                stipendId,
                null,
                new MarkPaidStipendRequestDTO("payment-123", Instant.now())
        )).isInstanceOf(FinancePrincipalRequiredException.class);
    }

    @Test
    void missingStipendReturnsNotFound() {
        UUID stipendId = UUID.randomUUID();
        when(stipendRepository.findByIdForUpdate(stipendId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(stipendId, "finance.user", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private MonthlyStipend stipend(UUID id, StipendStatus status) {
        return MonthlyStipend.builder()
                .id(id)
                .status(status)
                .build();
    }
}
