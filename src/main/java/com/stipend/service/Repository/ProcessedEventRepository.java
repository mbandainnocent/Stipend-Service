package com.stipend.service.Repository;

import com.stipend.service.Model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends
        JpaRepository<ProcessedEvent, UUID> {

}
