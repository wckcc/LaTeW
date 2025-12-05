package org.example.dto;

import lombok.Data;

/**
 * 导出请求DTO
 */
@Data
public class ExportRequest {
    private Long documentId;
    private String format; // LATEX, WORD, PDF
}

