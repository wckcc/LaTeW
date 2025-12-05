package org.example.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 编译结果DTO
 */
@Data
public class CompileResult {
    private Long id;
    private Long documentId;
    private String status; // SUCCESS, ERROR, WARNING
    private String errorMessage;
    private String logContent;
    private String compilerVersion;
    private Long compileTimeMs;
    private LocalDateTime createdAt;
    private String pdfPath; // 编译成功后的PDF路径
}

