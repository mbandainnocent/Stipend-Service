package com.stipend.service.Repository;

import com.stipend.service.Model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentRegistrationRepository extends JpaRepository<Student, UUID> {
}
