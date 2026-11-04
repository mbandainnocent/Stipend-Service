package com.stipend.service.Repository;

import com.stipend.service.Model.StipendPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = "tiers")
    @Query(
            value = """
                    select policy
                    from StipendPolicy policy
                    where (:programId is null or policy.programId = :programId)
                      and (:cohortId is null or policy.cohortId = :cohortId)
                      and (:enabled is null or policy.enabled = :enabled)
                    """,
            countQuery = """
                    select count(policy)
                    from StipendPolicy policy
                    where (:programId is null or policy.programId = :programId)
                      and (:cohortId is null or policy.cohortId = :cohortId)
                      and (:enabled is null or policy.enabled = :enabled)
                    """
    )
    Page<StipendPolicy> searchPolicies(
            @Param("programId") UUID programId,
            @Param("cohortId") UUID cohortId,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );

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
