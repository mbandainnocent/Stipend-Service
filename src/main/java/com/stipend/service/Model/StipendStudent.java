package com.stipend.service.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stipend_students")

public class StipendStudent {
    @Id
    private UUID studentId;

    private String firstName;
    private String lastName;

    private UUID programId;
    private UUID cohortId;

    private boolean active;

    private Instant registeredAt;
    private Instant updatedAt;
}
