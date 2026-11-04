package com.stipend.service.Repository;

import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Model.MonthlyStipendFinanceView;
import com.stipend.service.Enum.StipendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    Page<MonthlyStipend> findByStudentIdAndStipendYear(
            UUID studentId,
            int stipendYear,
            Pageable pageable
    );

    @Query(
            value = """
                    select stipend
                    from MonthlyStipend stipend
                    where (:year is null or stipend.stipendYear = :year)
                      and (:month is null or stipend.stipendMonth = :month)
                      and (:cohortId is null or stipend.cohortId = :cohortId)
                      and (:studentId is null or stipend.studentId = :studentId)
                      and (:status is null or stipend.status = :status)
                    """,
            countQuery = """
                    select count(stipend)
                    from MonthlyStipend stipend
                    where (:year is null or stipend.stipendYear = :year)
                      and (:month is null or stipend.stipendMonth = :month)
                      and (:cohortId is null or stipend.cohortId = :cohortId)
                      and (:studentId is null or stipend.studentId = :studentId)
                      and (:status is null or stipend.status = :status)
                    """
    )
    Page<MonthlyStipend> search(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("cohortId") UUID cohortId,
            @Param("studentId") UUID studentId,
            @Param("status") StipendStatus status,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.stipend.service.Model.MonthlyStipendFinanceView(
                        stipend.id,
                        student.studentId,
                        student.firstName,
                        student.lastName,
                        student.cohortId,
                        student.cohortStartDate,
                        student.cohortEndDate,
                        stipend.stipendYear,
                        stipend.stipendMonth,
                        stipend.calculatedAmount,
                        stipend.currency,
                        stipend.status
                    )
                    from MonthlyStipend stipend
                    join Student student on student.studentId = stipend.studentId
                    where (:year is null or stipend.stipendYear = :year)
                      and (:month is null or stipend.stipendMonth = :month)
                      and (:cohortId is null or stipend.cohortId = :cohortId)
                      and (:studentId is null or stipend.studentId = :studentId)
                      and (:status is null or stipend.status = :status)
                    """,
            countQuery = """
                    select count(stipend)
                    from MonthlyStipend stipend
                    join Student student on student.studentId = stipend.studentId
                    where (:year is null or stipend.stipendYear = :year)
                      and (:month is null or stipend.stipendMonth = :month)
                      and (:cohortId is null or stipend.cohortId = :cohortId)
                      and (:studentId is null or stipend.studentId = :studentId)
                      and (:status is null or stipend.status = :status)
                    """
    )
    Page<MonthlyStipendFinanceView> searchFinanceView(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("cohortId") UUID cohortId,
            @Param("studentId") UUID studentId,
            @Param("status") StipendStatus status,
            Pageable pageable
    );

    boolean existsByStipendYearAndStipendMonth(int stipendYear, int stipendMonth);
}
