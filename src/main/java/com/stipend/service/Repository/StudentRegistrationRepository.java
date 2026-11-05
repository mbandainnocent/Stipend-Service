package com.stipend.service.Repository;

import com.stipend.service.Enum.StudentStatus;
import com.stipend.service.Model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRegistrationRepository extends JpaRepository<Student, UUID> {
    List<Student> findByStatusAndStipendEligibleTrue(StudentStatus status);

    @Query("select min(student.cohortStartDate) from Student student")
    Optional<LocalDate> findEarliestCohortStartDate();
}
