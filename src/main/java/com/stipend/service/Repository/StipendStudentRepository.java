package com.stipend.service.Repository;

import com.stipend.service.Model.StipendStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StipendStudentRepository extends JpaRepository<StipendStudent, UUID> {
}
