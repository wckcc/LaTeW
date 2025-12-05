package org.example.dto;

import lombok.Data;

/**
 * AI请求DTO
 */
@Data
public class AIRequest {
    private String content; // LaTeX内容或错误信息
    private String type; // ERROR_ANALYSIS, OPTIMIZE, FIX_SYNTAX
}

