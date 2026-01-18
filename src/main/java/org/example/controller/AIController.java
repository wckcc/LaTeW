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


}

