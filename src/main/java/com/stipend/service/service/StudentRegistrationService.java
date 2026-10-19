package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.StudentRegistrationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class StudentRegistrationService {

    private final StudentRegistrationRepository studentRegistrationRepository;
    private final ZoneId cohortDateZone;

    public StudentRegistrationService(
            StudentRegistrationRepository studentRegistrationRepository,
            @Value("${app.registration.business-time-zone:Africa/Kigali}")
            String businessTimeZone) {
        this.studentRegistrationRepository = studentRegistrationRepository;
        this.cohortDateZone = ZoneId.of(businessTimeZone);
    }

    public void register(StudentRegisteredEvent event) {
        Student student = studentRegistrationRepository.findById(event.studentId())
                .orElseGet(() -> Student.builder()
                        .studentId(event.studentId())
                        .active(true)
                        .build());

        student.setStudentId(event.studentId());
        student.setFirstName(event.firstName());
        student.setLastName(event.lastName());
        student.setProgramId(event.programId());
        student.setCohortId(event.cohortId());
        LocalDate cohortStartDate = event.cohortStartDate()
                .atZone(cohortDateZone)
                .toLocalDate();
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
        student.setStipendEligible(student.isActive() && event.stipendEligible());
        student.setStipendBaseAmount(event.monthlyStipendAmount());
        student.setStipendCurrency(event.stipendCurrency());

        studentRegistrationRepository.save(student);
    }
}
