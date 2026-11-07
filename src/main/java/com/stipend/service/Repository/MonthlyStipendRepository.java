package com.stipend.service.Repository;

import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.Model.MonthlyStipendFinanceView;
import com.stipend.service.Enum.StipendStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlyStipendRepository extends JpaRepository<MonthlyStipend, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select stipend from MonthlyStipend stipend where stipend.id = :stipendId")
    Optional<MonthlyStipend> findByIdForUpdate(@Param("stipendId") UUID stipendId);

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
                        stipend.policyId,
                        stipend.stipendYear,
                        stipend.stipendMonth,
                        stipend.absenceCount,
                        stipend.scheduledTrainingDays,
                        stipend.eligibleTrainingDays,
                        stipend.creditedAttendanceDays,
                        stipend.attendancePercentage,
                        stipend.unauthorizedAbsenceDays,
                        stipend.baseAmount,
                        stipend.paymentPercentage,
                        stipend.calculatedAmount,
                        stipend.currency,
                        stipend.status,
                        stipend.approvedBy,
                        stipend.approvalComment,
                        stipend.approvedAt,
                        stipend.paymentReference,
                        stipend.paidAt,
                        stipend.calculatedAt,
                        stipend.updatedAt
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
}
