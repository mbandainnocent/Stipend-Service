package com.stipend.service.DTO;

import java.util.List;

public record MonthlyStipendPageDTO(
        List<MonthlyStipendResponseDTO> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
