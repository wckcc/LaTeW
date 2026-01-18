package org.example.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.dto.AIRequest;
import org.example.dto.AIResponse;
import org.example.dto.PdfToLatexResponse;
import org.example.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * AI助手服务实现类
 */
@Service
public class AIServiceImpl implements AIService {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${volcengine.api.url}")
    private String apiUrl;

    @Value("${volcengine.api.api-key}")
    private String apiKey;

    @Value("${volcengine.api.model}")
    private String model;

    @Value("${volcengine.api.timeout:120}")
    private int timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从PDF文件中提取文本内容
     */
    private String extractTextFromPdf(MultipartFile file) throws Exception {
        byte[] pdfBytes = file.getBytes();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            
            return stripper.getText(document);
        }
    }

    /**
     * 调用火山引擎 API 将PDF文本转换为LaTeX
     */
    private String callVolcengineApi(String pdfText) throws Exception {
        WebClient webClient = webClientBuilder.build();

        // 构建请求消息
        String systemPrompt = "你是一个专业的LaTeX文档转换专家。请将以下PDF文档的文本内容转换为标准、完整、可编译的LaTeX代码。要求：\n" +
                "1. 使用标准的LaTeX文档结构（\\documentclass, \\begin{document}等）\n" +
                "2. 保持原文档的格式和结构（标题、段落、列表等）\n" +
                "3. 正确处理数学公式（使用$...$或\\[...\\]）\n" +
                "4. 保持章节、段落等层次结构\n" +
                "5. 确保输出的LaTeX代码可以直接编译\n" +
                "6. 只输出LaTeX代码，不要添加任何解释性文字";

        String userPrompt = "请将以下PDF内容转换为LaTeX代码：\n\n" + pdfText;

        // 构建请求体（火山引擎 API 格式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        
        requestBody.put("messages", new Object[]{
            systemMessage,
            userMessage
        });
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 8000);

        // 发送请求
        String responseBody = webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeout))
                .block();

        // 解析响应
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message != null) {
                JsonNode content = message.get("content");
                if (content != null) {
                    return content.asText();
                }
            }
        }

        throw new Exception("火山引擎 API 返回格式错误: " + responseBody);
    }

    @Override
    public PdfToLatexResponse convertPdfToLatex(MultipartFile file) {
        PdfToLatexResponse response = new PdfToLatexResponse();
        
        try {
            // 验证文件类型
            if (file == null || file.isEmpty()) {
                response.setErrorMessage("PDF文件不能为空");
                return response;
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                response.setErrorMessage("请上传PDF格式的文件");
                return response;
            }

            // 提取PDF文本
            String pdfText = extractTextFromPdf(file);
            
            if (pdfText == null || pdfText.trim().isEmpty()) {
                response.setErrorMessage("PDF文件中没有提取到文本内容");
                return response;
            }

            // 限制文本长度（DeepSeek API有token限制）
            if (pdfText.length() > 30000) {
                pdfText = pdfText.substring(0, 30000) + "\n\n[内容过长，已截断]";
            }

            // 调用火山引擎 API 转换为LaTeX
            String latexContent = callVolcengineApi(pdfText);
            
            // 清理和验证LaTeX代码
            if (latexContent == null || latexContent.trim().isEmpty()) {
                response.setErrorMessage("AI转换失败：未返回LaTeX内容");
                return response;
            }

            // 确保LaTeX代码包含基本结构
            latexContent = latexContent.trim();
            if (!latexContent.contains("\\documentclass")) {
                // 如果没有文档类，添加基本结构
                latexContent = "\\documentclass{article}\n\\usepackage[utf8]{inputenc}\n\\usepackage{amsmath}\n\\usepackage{amsfonts}\n\\usepackage{amssymb}\n\\begin{document}\n\n" 
                    + latexContent + "\n\n\\end{document}";
            }

            response.setLatexContent(latexContent);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.setErrorMessage("转换失败: " + e.getMessage());
        }
        
        return response;
    }

    @Override
    public AIResponse processWithAI(AIRequest request) {
        AIResponse response = new AIResponse();
        
        try {
            WebClient webClient = webClientBuilder.build();

            String systemPrompt = "你是一个专业的LaTeX专家助手。请分析用户提供的LaTeX代码，并根据请求类型提供帮助。";
            String userPrompt = "";

            switch (request.getType()) {
                case "ERROR_ANALYSIS":
                    systemPrompt = "你是一个LaTeX错误分析专家。请分析以下错误信息并提供解决方案。";
                    userPrompt = "错误信息：\n" + request.getContent();
                    break;
                case "OPTIMIZE":
                    systemPrompt = "你是一个LaTeX代码优化专家。请优化以下LaTeX代码，提高其质量和可读性。";
                    userPrompt = "请优化以下LaTeX代码：\n" + request.getContent();
                    break;
                case "FIX_SYNTAX":
                    systemPrompt = "你是一个LaTeX语法修复专家。请修复以下LaTeX代码中的语法错误。";
                    userPrompt = "请修复以下LaTeX代码中的错误：\n" + request.getContent();
                    break;
                default:
                    userPrompt = request.getContent();
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            
            requestBody.put("messages", new Object[]{
                systemMessage,
                userMessage
            });

            String responseBody = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeout))
                    .block();

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        response.setResult(content.asText());
                        response.setSuggestion("处理完成");
                    }
                }
            }

            if (response.getResult() == null) {
                response.setResult("");
                response.setSuggestion("AI处理失败: " + responseBody);
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setResult("");
            response.setSuggestion("处理失败: " + e.getMessage());
        }
        
        return response;
    }
}

