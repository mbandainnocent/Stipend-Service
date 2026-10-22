package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Enum.StudentStatus;
import com.stipend.service.Model.StipendPolicy;
import com.stipend.service.Model.StipendTier;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.StipendPolicyRepository;
import com.stipend.service.Repository.StudentRegistrationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class StudentRegistrationService {

    private final StudentRegistrationRepository studentRegistrationRepository;
    private final StipendPolicyRepository stipendPolicyRepository;
    private final ZoneId cohortDateZone;

    public StudentRegistrationService(
            StudentRegistrationRepository studentRegistrationRepository,
            StipendPolicyRepository stipendPolicyRepository,
            @Value("${app.registration.business-time-zone:Africa/Kigali}")
            String businessTimeZone) {
        this.studentRegistrationRepository = studentRegistrationRepository;
        this.stipendPolicyRepository = stipendPolicyRepository;
        this.cohortDateZone = ZoneId.of(businessTimeZone);
    }

    @Transactional
    public void register(StudentRegisteredEvent event) {
        Student student = studentRegistrationRepository.findById(event.studentId())
                .orElseGet(() -> Student.builder()
                        .studentId(event.studentId())
                        .status(StudentStatus.ACTIVE)
                        .build());

        student.setStudentId(event.studentId());
        student.setFirstName(event.firstName());
        student.setLastName(event.lastName());
        student.setProgramId(event.programId());
        student.setCohortId(event.cohortId());
        LocalDate cohortStartDate = event.cohortStartDate()
                .atZone(cohortDateZone)
                .toLocalDate();
        ensureDefaultPolicy(event, cohortStartDate);
        student.setCohortStartDate(cohortStartDate);
        student.setCohortEndDate(
                event.cohortEndDate()
                        .atZone(cohortDateZone)
                        .toLocalDate()
        );
        student.setRegisteredAt(event.registeredAt());
        student.setRegistrationDate(event.registrationDate());
        student.setVersion(event.version());
        student.setUpdatedAt(Instant.now());

        student.setCohortStipendEligible(event.stipendEligible());
        student.setStipendEligible(
                student.getStatus() == StudentStatus.ACTIVE && event.stipendEligible()
        );
        student.setStipendBaseAmount(event.monthlyStipendAmount());
        student.setStipendCurrency(event.stipendCurrency());

        studentRegistrationRepository.save(student);
    }

    private void ensureDefaultPolicy(StudentRegisteredEvent event, LocalDate effectiveFrom) {
        if (stipendPolicyRepository.existsByCohortId(event.cohortId())) {
            return;
        }

        StipendPolicy policy = StipendPolicy.builder()
                .programId(event.programId())
                .cohortId(event.cohortId())
                .enabled(true)
                .effectiveFrom(effectiveFrom)
                .tiers(List.of(
                        createTier(0, 2, "100.00"),
                        createTier(3, 3, "50.00"),
                        createTier(4, null, "0.00")

                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        policy.getTiers().forEach(tier -> tier.setPolicy(policy));
        stipendPolicyRepository.save(policy);
    }

    private StipendTier createTier(
            int minimumAbsences,
            Integer maximumAbsences,
            String paymentPercentage
    ) {
        return StipendTier.builder()
                .minimumAbsences(minimumAbsences)
                .maximumAbsences(maximumAbsences)
                .paymentPercentage(new BigDecimal(paymentPercentage))
                .build();
    }
}
