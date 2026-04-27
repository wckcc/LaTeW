package org.example.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * PDF/Word 导出文件记录
 */
@Data
public class PdfWordFileDTO {
    private Long id;
    private Long projectId;
    private String filename;
    private String filePath;
    private LocalDateTime uploadedAt;
}

