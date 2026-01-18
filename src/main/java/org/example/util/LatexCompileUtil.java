package org.example.util;

import org.example.dto.CompileResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * LaTeX编译工具类
 * 使用 Tex Live 编译 LaTeX 代码
 */
@Component
public class LatexCompileUtil {

    @Value("${latex.compile.temp-dir:./temp/compile}")
    private String tempDir;

    @Value("${latex.compile.output-dir:./static/pdf}")
    private String outputDir;

    @Value("${latex.compile.pdflatex-path:pdflatex}")
    private String pdflatexPath;

    @Value("${latex.compile.timeout:30}")
    private int timeoutSeconds;

    /**
     * 编译 LaTeX 代码为 PDF
     * 
     * @param latexContent LaTeX 代码内容
     * @param projectId 项目ID（用于生成文件名）
     * @param compiler 编译器类型（pdflatex, xelatex, lualatex）
     * @return 编译结果对象，包含PDF路径、日志等信息
     * @throws Exception 编译异常
     */
    public CompileResult compile(String latexContent, Long projectId, String compiler) throws Exception {
        long startTime = System.currentTimeMillis();
        
        // 创建临时目录（使用绝对路径）
        String compileId = UUID.randomUUID().toString();
        Path baseTempDir = Paths.get(tempDir).toAbsolutePath().normalize();
        Path tempWorkDir = baseTempDir.resolve(compileId);
        Files.createDirectories(tempWorkDir);
        
        // 创建输出目录（使用绝对路径）
        Path pdfOutputDir = Paths.get(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(pdfOutputDir);
        
        String texFileName = "main.tex";
        Path texFile = tempWorkDir.resolve(texFileName);
        
        try {
            // 写入 LaTeX 内容到文件
            Files.write(texFile, latexContent.getBytes("UTF-8"));
            
            // 确定编译器命令
            String compilerCommand = getCompilerCommand(compiler);
            
            // 执行编译命令
            // 注意：由于已经设置了工作目录，不需要使用 -output-directory 参数
            // 不使用 -halt-on-error，允许在有警告时继续编译（如图片文件缺失）
            ProcessBuilder processBuilder = new ProcessBuilder(
                compilerCommand,
                "-interaction=nonstopmode",
                texFileName
            );
            
            // 设置工作目录为临时目录（使用绝对路径的 File 对象）
            processBuilder.directory(tempWorkDir.toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 读取编译输出
            StringBuilder logOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logOutput.append(line).append("\n");
                }
            }
            
            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("编译超时（超过 " + timeoutSeconds + " 秒）");
            }
            
            int exitCode = process.exitValue();
            long compileTime = System.currentTimeMillis() - startTime;
            
            // 检查PDF是否生成（即使退出码不为0，如果PDF生成了也认为成功）
            Path pdfFile = tempWorkDir.resolve("main.pdf");
            CompileResult result = new CompileResult();
            result.setCompileTimeMs(compileTime);
            result.setLogContent(logOutput.toString());
            result.setCreatedAt(LocalDateTime.now());
            
            // 检查PDF是否生成
            String logContent = logOutput.toString();
            
            if (Files.exists(pdfFile)) {
                // PDF 文件已生成，认为编译成功
                String pdfFileName = "project_" + projectId + "_" + compileId + ".pdf";
                Path outputPdf = pdfOutputDir.resolve(pdfFileName);
                Files.copy(pdfFile, outputPdf);
                
                // 检查是否有警告或非致命错误（如图片文件缺失等）
                // 如果 PDF 生成了，即使有错误消息，也视为成功或警告
                boolean hasNonFatalErrors = logContent.contains("File") && logContent.contains("not found");
                boolean hasWarnings = logContent.contains("Warning") || logContent.contains("warning");
                boolean hasErrors = logContent.contains("!") && !hasNonFatalErrors;
                
                if (hasWarnings || hasNonFatalErrors) {
                    // 有警告或非致命错误（如图片缺失），但 PDF 已生成
                    result.setStatus("WARNING");
                    // 提取警告信息作为提示
                    String warningMsg = extractWarningMessage(logContent);
                    if (warningMsg != null && !warningMsg.isEmpty()) {
                        result.setErrorMessage(warningMsg);
                    } else if (hasNonFatalErrors) {
                        // 提取文件缺失的错误信息
                        result.setErrorMessage(extractFileNotFoundMessage(logContent));
                    }
                } else if (hasErrors && exitCode != 0) {
                    // 有错误且退出码不为0，但 PDF 仍然生成了（少见情况）
                    result.setStatus("WARNING");
                    result.setErrorMessage(extractErrorMessage(logContent));
                } else {
                    // 完全成功
                    result.setStatus("SUCCESS");
                }
                
                result.setPdfPath("/api/pdf/" + pdfFileName); // 相对路径，用于前端访问
                
                // 获取编译器版本
                String version = getCompilerVersion(compilerCommand);
                result.setCompilerVersion(version);
            } else {
                // 编译失败，没有生成PDF
                result.setStatus("ERROR");
                result.setErrorMessage(extractErrorMessage(logContent));
                result.setPdfPath(null);
            }
            
            return result;
            
        } finally {
            // 清理临时文件（可选：保留日志时注释掉）
            deleteDirectory(tempWorkDir.toFile());
        }
    }
    
    /**
     * 获取编译器命令
     */
    private String getCompilerCommand(String compiler) {
        if (compiler == null || compiler.isEmpty()) {
            compiler = "pdflatex";
        }
        
        switch (compiler.toLowerCase()) {
            case "xelatex":
                return "xelatex";
            case "lualatex":
                return "lualatex";
            case "pdflatex":
            default:
                // 如果配置了完整路径，使用配置的路径，否则使用系统PATH中的命令
                return pdflatexPath;
        }
    }
    
    /**
     * 获取编译器版本信息
     */
    private String getCompilerVersion(String compilerCommand) {
        try {
            ProcessBuilder pb = new ProcessBuilder(compilerCommand, "--version");
            Process process = pb.start();
            
            StringBuilder versionOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 3) {
                    versionOutput.append(line).append(" ");
                    lineCount++;
                }
            }
            
            process.waitFor(5, TimeUnit.SECONDS);
            return versionOutput.toString().trim();
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * 从日志中提取错误信息
     */
    private String extractErrorMessage(String logContent) {
        String[] lines = logContent.split("\n");
        StringBuilder errorMsg = new StringBuilder();
        
        boolean inError = false;
        for (String line : lines) {
            if (line.contains("!") || line.contains("Error") || line.contains("error")) {
                inError = true;
                errorMsg.append(line).append("\n");
            } else if (inError && (line.trim().isEmpty() || line.startsWith("l."))) {
                if (line.startsWith("l.")) {
                    errorMsg.append(line).append("\n");
                }
                if (line.trim().isEmpty() && errorMsg.length() > 0) {
                    break;
                }
            }
        }
        
        if (errorMsg.length() == 0) {
            errorMsg.append("编译失败，请查看完整日志");
        }
        
        return errorMsg.toString().trim();
    }
    
    /**
     * 从日志中提取警告信息
     */
    private String extractWarningMessage(String logContent) {
        String[] lines = logContent.split("\n");
        StringBuilder warningMsg = new StringBuilder();
        
        for (String line : lines) {
            // 提取常见的警告信息
            if (line.contains("Warning") || line.contains("warning")) {
                warningMsg.append(line).append("\n");
            }
        }
        
        return warningMsg.toString().trim();
    }
    
    /**
     * 从日志中提取文件缺失的错误信息（这类错误通常是非致命的）
     */
    private String extractFileNotFoundMessage(String logContent) {
        String[] lines = logContent.split("\n");
        StringBuilder msg = new StringBuilder();
        
        for (String line : lines) {
            // 查找文件缺失相关的错误
            if (line.contains("File") && line.contains("not found")) {
                // 找到包含文件名的那一行
                if (line.contains("!")) {
                    msg.append(line.trim()).append(" ");
                } else {
                    // 继续查找相关的错误信息
                    msg.append(line.trim()).append(" ");
                }
            }
            // 限制消息长度
            if (msg.length() > 500) {
                break;
            }
        }
        
        String result = msg.toString().trim();
        return result.isEmpty() ? "某些文件未找到（如图片文件），但 PDF 已生成" : result;
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}

