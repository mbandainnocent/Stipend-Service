package com.stipend.service.service;

import com.stipend.service.DTO.ApproveStipendRequestDTO;
import com.stipend.service.DTO.MarkPaidStipendRequestDTO;
import com.stipend.service.DTO.MonthlyStipendResponseDTO;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Exception.FinancePrincipalRequiredException;
import com.stipend.service.Exception.InvalidStipendTransitionException;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Repository.MonthlyStipendRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class FinanceStipendWorkflowService {

    private static final int MAX_APPROVAL_COMMENT_LENGTH = 500;
    private static final int MAX_PAYMENT_REFERENCE_LENGTH = 100;

    private final MonthlyStipendRepository monthlyStipendRepository;
    private final FinanceStipendReadService financeStipendReadService;

    public FinanceStipendWorkflowService(
            MonthlyStipendRepository monthlyStipendRepository,
            FinanceStipendReadService financeStipendReadService
    ) {
        this.monthlyStipendRepository = monthlyStipendRepository;
        this.financeStipendReadService = financeStipendReadService;
    }

    @Transactional
    public MonthlyStipendResponseDTO approve(
            UUID stipendId,
            String approver,
            ApproveStipendRequestDTO request
    ) {
        requireApprover(approver);
        String comment = request == null ? null : request.comment();
        if (comment != null && comment.length() > MAX_APPROVAL_COMMENT_LENGTH) {
            throw new IllegalArgumentException("comment must not exceed 500 characters");
        }

        MonthlyStipend stipend = getForUpdate(stipendId);
        requireStatus(stipend, StipendStatus.CALCULATED, StipendStatus.APPROVED);
        Instant now = Instant.now();
        stipend.setStatus(StipendStatus.APPROVED);
        stipend.setApprovedBy(approver);
        stipend.setApprovalComment(comment);
        stipend.setApprovedAt(now);
        stipend.setUpdatedAt(now);
        monthlyStipendRepository.save(stipend);
        return financeStipendReadService.getStipend(stipendId);
    }

    @Transactional
    public MonthlyStipendResponseDTO markPaid(
            UUID stipendId,
            String financeUser,
            MarkPaidStipendRequestDTO request
    ) {
        requireFinanceUser(financeUser);
        if (request == null) {
            throw new IllegalArgumentException("payment request body is required");
        }
        String paymentReference = request.paymentReference();
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("paymentReference must not be blank");
        }
        if (paymentReference.length() > MAX_PAYMENT_REFERENCE_LENGTH) {
            throw new IllegalArgumentException("paymentReference must not exceed 100 characters");
        }
        if (request.paidAt() == null) {
            throw new IllegalArgumentException("paidAt is required");
        }

        MonthlyStipend stipend = getForUpdate(stipendId);
        requireStatus(stipend, StipendStatus.APPROVED, StipendStatus.PAID);
        stipend.setStatus(StipendStatus.PAID);
        stipend.setPaymentReference(paymentReference);
        stipend.setPaidAt(request.paidAt());
        stipend.setUpdatedAt(Instant.now());
        monthlyStipendRepository.save(stipend);
        return financeStipendReadService.getStipend(stipendId);
    }

    private MonthlyStipend getForUpdate(UUID stipendId) {
        return monthlyStipendRepository.findByIdForUpdate(stipendId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Monthly stipend not found: " + stipendId
                ));
    }

    private void requireStatus(MonthlyStipend stipend, StipendStatus required, StipendStatus target) {
        if (stipend.getStatus() != required) {
            throw new InvalidStipendTransitionException(
                    "Cannot transition stipend from " + stipend.getStatus() + " to " + target
                            + "; expected " + required
            );
        }
    }

    private void requireApprover(String approver) {
        requireFinanceUser(approver);
    }

    private void requireFinanceUser(String financeUser) {
        if (financeUser == null || financeUser.isBlank()) {
            throw new FinancePrincipalRequiredException();
        }
    }
}
