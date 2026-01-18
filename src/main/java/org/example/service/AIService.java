package org.example.service;

import org.example.dto.AIRequest;
import org.example.dto.AIResponse;
import org.example.dto.PdfToLatexRequest;
import org.example.dto.PdfToLatexResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI助手服务接口
 */
public interface AIService {

    /**
     * 将PDF文件转换为LaTeX代码
     * @param file PDF文件
     * @return PDF转LaTeX响应
     */
    PdfToLatexResponse convertPdfToLatex(MultipartFile file);

    /**
     * 使用AI处理LaTeX内容（错误分析、优化等）
     * @param request AI请求
     * @return AI响应
     */
    AIResponse processWithAI(AIRequest request);
}

