package com.stipend.service.DTO;

import lombok.Data;

import java.util.Date;
import java.util.UUID;

@Data
public class StipendPolicyRequestDTO {
    private UUID policyId;

    private UUID programId;

    private UUID cohortId;

    private String policyName;

    private String description;

    private Boolean isEnabled;

    private Date startDate;

    private Date endDate;

    private Double monthlyBaseAmount;


}
