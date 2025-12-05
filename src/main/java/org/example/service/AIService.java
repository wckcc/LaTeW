package org.example.service;

import org.example.dto.AIRequest;
import org.example.dto.AIResponse;

/**
 * AI助手服务接口
 */
public interface AIService {
    /**
     * 分析错误报告并提供建议
     */
    AIResponse analyzeError(String errorMessage);

    /**
     * 优化LaTeX排版
     */
    AIResponse optimizeLaTeX(String latexContent);

    /**
     * 修复LaTeX语法错误
     */
    AIResponse fixLaTeXSyntax(String latexContent, String errorMessage);

    /**
     * 通用AI处理
     */
    AIResponse processAIRequest(AIRequest request);
}

