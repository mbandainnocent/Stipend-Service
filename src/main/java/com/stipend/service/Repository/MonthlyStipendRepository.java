package com.stipend.service.Repository;

import com.stipend.service.Model.MonthlyStipend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlyStipendRepository extends JpaRepository<MonthlyStipend, UUID> {
    Optional<MonthlyStipend> findByStudentIdAndStipendYearAndStipendMonth(
            UUID studentId,
            int stipendYear,
            int stipendMonth
    );
}
