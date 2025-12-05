package org.example.service.impl;

import org.example.dto.AIRequest;
import org.example.dto.AIResponse;
import org.example.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * AI助手服务实现类
 */
@Service
public class AIServiceImpl implements AIService {

    @Autowired(required = false)
    private WebClient.Builder webClientBuilder;

    @Value("${ai.api.url:}")
    private String aiApiUrl;

    @Value("${ai.api.key:}")
    private String aiApiKey;

    @Value("${ai.enabled:false}")
    private boolean aiEnabled;

    @Override
    public AIResponse analyzeError(String errorMessage) {
        if (!aiEnabled) {
            return createDefaultErrorAnalysis(errorMessage);
        }

        AIRequest request = new AIRequest();
        request.setContent(errorMessage);
        request.setType("ERROR_ANALYSIS");

        return processAIRequest(request);
    }

    @Override
    public AIResponse optimizeLaTeX(String latexContent) {
        if (!aiEnabled) {
            return createDefaultOptimization(latexContent);
        }

        AIRequest request = new AIRequest();
        request.setContent(latexContent);
        request.setType("OPTIMIZE");

        return processAIRequest(request);
    }

    @Override
    public AIResponse fixLaTeXSyntax(String latexContent, String errorMessage) {
        if (!aiEnabled) {
            return createDefaultFix(latexContent, errorMessage);
        }

        AIRequest request = new AIRequest();
        request.setContent(latexContent + "\n\n错误信息:\n" + errorMessage);
        request.setType("FIX_SYNTAX");

        return processAIRequest(request);
    }

    @Override
    public AIResponse processAIRequest(AIRequest request) {
        if (!aiEnabled || aiApiUrl == null || aiApiUrl.isEmpty()) {
            // 返回默认响应
            return createDefaultResponse(request);
        }

        try {
            // 检查API配置
            String apiUrl = aiApiUrl;
            String apiKey = aiApiKey;
            if (apiUrl == null || apiUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
                return createDefaultResponse(request);
            }

            // 构建WebClient
            WebClient webClient = (webClientBuilder != null) ? 
                webClientBuilder.build() : WebClient.builder().build();

            // 构建请求体
            String prompt = buildPrompt(request);
            String requestBody = buildRequestBody(prompt);
            if (requestBody == null) {
                return createDefaultResponse(request);
            }

            // 调用AI API（这里使用通用格式，实际需要根据具体AI服务调整）
            Mono<AIResponse> responseMono = webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(AIResponse.class)
                .timeout(Duration.ofSeconds(30))
                .onErrorReturn(createDefaultResponse(request));

            return responseMono.block();
        } catch (Exception e) {
            // 发生异常时返回默认响应
            return createDefaultResponse(request);
        }
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(AIRequest request) {
        String type = request.getType();
        String content = request.getContent();

        switch (type) {
            case "ERROR_ANALYSIS":
                return "请分析以下LaTeX编译错误，并提供修复建议：\n\n" + content;
            case "OPTIMIZE":
                return "请优化以下LaTeX代码的排版和结构：\n\n" + content;
            case "FIX_SYNTAX":
                return "请修复以下LaTeX代码中的语法错误：\n\n" + content;
            default:
                return content;
        }
    }

    /**
     * 构建请求体（根据实际AI服务API格式调整）
     */
    private String buildRequestBody(String prompt) {
        // 这里需要根据实际使用的AI服务API格式来构建
        // 例如OpenAI格式、Claude格式等
        if (prompt == null) {
            prompt = "";
        }
        String escapedPrompt = prompt.replace("\"", "\\\"");
        return String.format("{\"prompt\": \"%s\", \"max_tokens\": 1000}", escapedPrompt);
    }

    /**
     * 创建默认的错误分析响应
     */
    private AIResponse createDefaultErrorAnalysis(String errorMessage) {
        AIResponse response = new AIResponse();
        response.setResult("错误分析功能需要配置AI服务");
        response.setSuggestion("请检查LaTeX语法，常见错误包括：缺少闭合括号、未定义的命令、缺少包引用等。");
        return response;
    }

    /**
     * 创建默认的优化响应
     */
    private AIResponse createDefaultOptimization(String latexContent) {
        AIResponse response = new AIResponse();
        response.setResult("优化功能需要配置AI服务");
        response.setSuggestion("建议使用标准的LaTeX包和命令，保持代码结构清晰。");
        return response;
    }

    /**
     * 创建默认的修复响应
     */
    private AIResponse createDefaultFix(String latexContent, String errorMessage) {
        AIResponse response = new AIResponse();
        response.setResult("语法修复功能需要配置AI服务");
        response.setSuggestion("请根据错误信息检查代码，确保所有命令和包都正确引用。");
        return response;
    }

    /**
     * 创建默认响应
     */
    private AIResponse createDefaultResponse(AIRequest request) {
        AIResponse response = new AIResponse();
        response.setResult("AI服务未配置或不可用");
        response.setSuggestion("请配置AI服务API地址和密钥以使用AI功能。");
        return response;
    }
}

