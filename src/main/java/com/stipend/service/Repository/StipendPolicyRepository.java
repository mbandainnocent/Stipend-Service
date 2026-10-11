package com.stipend.service.Repository;

import com.stipend.service.Model.StipendPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StipendPolicyRepository extends JpaRepository<StipendPolicy, UUID> {
    Optional<StipendPolicy> findByCohortId(UUID cohortId);
}
