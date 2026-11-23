package com.stipend.service.DTO;

import jakarta.validation.constraints.Size;

public record ApproveStipendRequestDTO(
        @Size(max = 500, message = "comment must not exceed 500 characters")
        String comment
) {
}
