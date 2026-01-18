package org.example.controller;

import org.example.dto.AIRequest;
import org.example.dto.AIResponse;
import org.example.dto.PdfToLatexResponse;
import org.example.dto.ResponseResult;
import org.example.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI助手控制器
 * 提供AI辅助功能：错误分析、排版优化、语法修复等
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIController {

    @Autowired
    private AIService aiService;

    /**
     * 将PDF文件转换为LaTeX代码
     * POST /api/ai/pdf-to-latex
     */
    @PostMapping("/pdf-to-latex")
    public ResponseResult<PdfToLatexResponse> convertPdfToLatex(@RequestParam("file") MultipartFile file) {
        PdfToLatexResponse response = aiService.convertPdfToLatex(file);
        
        if (response.getErrorMessage() != null && !response.getErrorMessage().isEmpty()) {
            return ResponseResult.error(400, response.getErrorMessage());
        }
        
        return ResponseResult.success("PDF转换成功", response);
    }

    /**
     * AI处理LaTeX内容
     * POST /api/ai/process
     */
    @PostMapping("/process")
    public ResponseResult<AIResponse> processWithAI(@RequestBody AIRequest request) {
        AIResponse response = aiService.processWithAI(request);
        return ResponseResult.success("AI处理完成", response);
    }
}

