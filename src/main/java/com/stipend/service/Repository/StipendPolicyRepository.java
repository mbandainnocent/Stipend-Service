package com.stipend.service.Repository;

import com.stipend.service.Model.StipendPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StipendPolicyRepository extends JpaRepository<StipendPolicy, UUID> {
    Optional<StipendPolicy> findByCohortId(UUID cohortId);
    boolean existsByCohortId(UUID cohortId);

    @Query("""
            select distinct policy
            from StipendPolicy policy
            left join fetch policy.tiers
            where policy.cohortId = :cohortId
              and policy.enabled = true
              and policy.effectiveFrom <= :effectiveDate
              and (policy.effectiveTo is null or policy.effectiveTo >= :effectiveDate)
            """)
    Optional<StipendPolicy> findEffectivePolicyForCohort(
            @Param("cohortId") UUID cohortId,
            @Param("effectiveDate") LocalDate effectiveDate
    );
}
