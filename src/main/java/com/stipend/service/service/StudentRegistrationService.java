package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Model.StipendStudent;
import com.stipend.service.Repository.StipendStudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;

@Service
public class StudentRegistrationService {

    private final StipendStudentRepository stipendStudentRepository;
    private final ZoneId cohortDateZone;

    public StudentRegistrationService(
            StipendStudentRepository stipendStudentRepository,
            @Value("${app.registration.business-time-zone:Africa/Kigali}")
            String businessTimeZone) {
        this.stipendStudentRepository = stipendStudentRepository;
        this.cohortDateZone = ZoneId.of(businessTimeZone);
    }

    public void register(StudentRegisteredEvent event) {
        StipendStudent student = stipendStudentRepository
                .findById(event.studentId())
                .orElseGet(() -> StipendStudent.builder()
                        .studentId(event.studentId())
                        .build());

        student.setStudentId(event.studentId());
        student.setFirstName(event.firstName());
        student.setLastName(event.lastName());
        student.setProgramId(event.programId());
        student.setCohortId(event.cohortId());
        student.setCohortStartDate(
                event.cohortStartDate()
                        .atZone(cohortDateZone)
                        .toLocalDate()
        );
        student.setCohortEndDate(
                event.cohortEndDate()
                        .atZone(cohortDateZone)
                        .toLocalDate()
        );
        student.setRegisteredAt(event.registeredAt());
        student.setRegistrationDate(event.registrationDate());
        student.setVersion(event.version());
        student.setActive(true);
        student.setUpdatedAt(Instant.now());

        stipendStudentRepository.save(student);
    }
}
