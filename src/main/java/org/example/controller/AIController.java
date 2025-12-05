package org.example.controller;

import org.example.dto.AIRequest;
import org.example.dto.AIResponse;
import org.example.dto.ResponseResult;
import org.example.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
     * 分析LaTeX编译错误
     * POST /api/ai/analyze-error
     */
    @PostMapping("/analyze-error")
    public ResponseResult<AIResponse> analyzeError(@RequestBody String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return ResponseResult.error(400, "错误信息不能为空");
        }

        AIResponse response = aiService.analyzeError(errorMessage);
        return ResponseResult.success("错误分析完成", response);
    }

    /**
     * 优化LaTeX排版
     * POST /api/ai/optimize
     */
    @PostMapping("/optimize")
    public ResponseResult<AIResponse> optimizeLaTeX(@RequestBody String latexContent) {
        if (latexContent == null || latexContent.trim().isEmpty()) {
            return ResponseResult.error(400, "LaTeX内容不能为空");
        }

        AIResponse response = aiService.optimizeLaTeX(latexContent);
        return ResponseResult.success("优化完成", response);
    }

    /**
     * 修复LaTeX语法错误
     * POST /api/ai/fix-syntax
     */
    @PostMapping("/fix-syntax")
    public ResponseResult<AIResponse> fixLaTeXSyntax(
            @RequestParam("latexContent") String latexContent,
            @RequestParam("errorMessage") String errorMessage) {
        
        if (latexContent == null || latexContent.trim().isEmpty()) {
            return ResponseResult.error(400, "LaTeX内容不能为空");
        }
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return ResponseResult.error(400, "错误信息不能为空");
        }

        AIResponse response = aiService.fixLaTeXSyntax(latexContent, errorMessage);
        return ResponseResult.success("语法修复完成", response);
    }

    /**
     * 通用AI处理接口
     * POST /api/ai/process
     */
    @PostMapping("/process")
    public ResponseResult<AIResponse> processAIRequest(@RequestBody AIRequest request) {
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            return ResponseResult.error(400, "请求内容不能为空");
        }

        AIResponse response = aiService.processAIRequest(request);
        return ResponseResult.success("处理完成", response);
    }
}

