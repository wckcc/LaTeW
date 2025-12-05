package org.example.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档数据传输对象
 */
@Data
public class DocumentDTO {
    private Long id;
    private String title;
    private String content;
    private String htmlContent;
    private Long userId;
    private Long templateId;
    private String status;
    private String latexCompiler;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastCompiledAt;
}

