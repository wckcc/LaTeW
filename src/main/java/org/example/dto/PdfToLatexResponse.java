package org.example.dto;

import lombok.Data;

/**
 * PDF转LaTeX响应DTO
 */
@Data
public class PdfToLatexResponse {
    private String latexContent; // 转换后的LaTeX内容
    private String errorMessage; // 错误信息（如果有）
}

