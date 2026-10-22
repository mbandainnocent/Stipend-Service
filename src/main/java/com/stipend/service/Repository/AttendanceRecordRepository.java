package com.stipend.service.Repository;

import com.stipend.service.Enum.AttendanceStatus;
import com.stipend.service.Model.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
    @Query("""
            select distinct attendance.date
            from AttendanceRecord attendance
            where attendance.cohortId = :cohortId
              and attendance.date between :startDate and :endDate
            order by attendance.date
            """)
    List<LocalDate> findDistinctAttendanceDatesByCohortIdAndDateBetween(
            @Param("cohortId") UUID cohortId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select min(attendance.date)
            from AttendanceRecord attendance
            where attendance.studentId = :studentId
              and attendance.cohortId = :cohortId
              and attendance.date between :startDate and :endDate
              and attendance.status = :status
            """)
    Optional<LocalDate> findFirstAttendanceDateByStudentIdAndCohortIdAndStatusAndDateBetween(
            @Param("studentId") UUID studentId,
            @Param("cohortId") UUID cohortId,
            @Param("status") AttendanceStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select count(distinct attendance.date)
            from AttendanceRecord attendance
            where attendance.studentId = :studentId
              and attendance.cohortId = :cohortId
              and attendance.date between :startDate and :endDate
              and attendance.status = :status
            """)
    long countDistinctAttendanceDatesByStudentIdAndCohortIdAndStatusAndDateBetween(
            @Param("studentId") UUID studentId,
            @Param("cohortId") UUID cohortId,
            @Param("status") AttendanceStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select count(distinct attendance.date)
            from AttendanceRecord attendance
            where attendance.studentId = :studentId
              and attendance.cohortId = :cohortId
              and attendance.date between :startDate and :endDate
              and attendance.status in :statuses
            """)
    long countDistinctAttendanceDatesByStudentIdAndCohortIdAndDateBetweenAndStatusIn(
            @Param("studentId") UUID studentId,
            @Param("cohortId") UUID cohortId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<AttendanceStatus> statuses
    );
}
