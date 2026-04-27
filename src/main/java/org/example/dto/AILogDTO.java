package org.example.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI日志DTO
 */
@Data
public class AILogDTO {
    private Long id;
    private Long projectId;
    private String requestType;
    private String inputContent;
    private String outputContent;
    private LocalDateTime createdAt;
}

