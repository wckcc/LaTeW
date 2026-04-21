package org.example.util;

import org.example.dto.CompileResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
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

    @Value("${latex.compile.xelatex-path:xelatex}")
    private String xelatexPath;

    @Value("${latex.compile.lualatex-path:lualatex}")
    private String lualatexPath;

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
            String normalizedCompiler = normalizeCompiler(compiler);
            String compilerCommand = getCompilerCommand(normalizedCompiler);
            
            CompileExecutionResult execution = runCompileCommand(compilerCommand, texFileName, tempWorkDir);
            StringBuilder logOutput = new StringBuilder(execution.logContent);
            int exitCode = execution.exitCode;
            long compileTime = System.currentTimeMillis() - startTime;
            
            // 检查PDF是否生成（即使退出码不为0，如果PDF生成了也认为成功）
            Path pdfFile = tempWorkDir.resolve("main.pdf");
            if (!Files.exists(pdfFile) && hasMissingGraphicsError(logOutput.toString())) {
                // 图片缺失时自动降级重试：将 includegraphics 替换为占位框，保证可预览
                String draftContent = buildMissingImageTolerantContent(latexContent);
                Files.write(texFile, draftContent.getBytes("UTF-8"));
                CompileExecutionResult retryExecution = runCompileCommand(compilerCommand, texFileName, tempWorkDir);
                logOutput.append("\n[retry-with-graphicx-draft]\n").append(retryExecution.logContent);
                exitCode = retryExecution.exitCode;
            }

            pdfFile = tempWorkDir.resolve("main.pdf");
            if (!Files.exists(pdfFile) && "xelatex".equals(normalizedCompiler)) {
                // 某些 XeLaTeX 环境会产出 XDV，需要额外转换为 PDF
                Path xdvFile = tempWorkDir.resolve("main.xdv");
                if (Files.exists(xdvFile)) {
                    String xdvLog = convertXdvToPdf(tempWorkDir, xdvFile);
                    if (xdvLog != null && !xdvLog.isEmpty()) {
                        logOutput.append("\n[xdvipdfmx]\n").append(xdvLog).append("\n");
                    }
                }
            }
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
                Files.copy(pdfFile, outputPdf, StandardCopyOption.REPLACE_EXISTING);
                
                // 校验生成文件是否为有效PDF，避免前端出现“无法打开此文件”
                if (!isValidPdf(outputPdf)) {
                    // 有些 XeLaTeX 场景会先产出损坏 PDF，同时日志提示图片缺失；此时强制走占位图重试
                    if (hasMissingGraphicsError(logContent)) {
                        String tolerantContent = buildMissingImageTolerantContent(latexContent);
                        Files.write(texFile, tolerantContent.getBytes("UTF-8"));
                        CompileExecutionResult retryExecution = runCompileCommand(compilerCommand, texFileName, tempWorkDir);
                        logOutput.append("\n[retry-on-invalid-pdf-with-missing-image]\n").append(retryExecution.logContent);
                        logContent = logOutput.toString();
                        pdfFile = tempWorkDir.resolve("main.pdf");
                        if ("xelatex".equals(normalizedCompiler) && !Files.exists(pdfFile)) {
                            Path xdvFile = tempWorkDir.resolve("main.xdv");
                            if (Files.exists(xdvFile)) {
                                String xdvLog = convertXdvToPdf(tempWorkDir, xdvFile);
                                if (xdvLog != null && !xdvLog.isEmpty()) {
                                    logOutput.append("\n[xdvipdfmx-retry]\n").append(xdvLog).append("\n");
                                    logContent = logOutput.toString();
                                }
                            }
                        }
                        if (Files.exists(pdfFile)) {
                            Files.copy(pdfFile, outputPdf, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }

                    if (!isValidPdf(outputPdf)) {
                        result.setStatus("ERROR");
                        String logHint = extractErrorMessage(logContent);
                        if (logHint == null || logHint.isEmpty()) {
                            logHint = "编译产物不是有效的 PDF 文件";
                        }
                        result.setErrorMessage(logHint);
                        result.setPdfPath(null);
                        return result;
                    }
                }
                
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

    private CompileExecutionResult runCompileCommand(String compilerCommand, String texFileName, Path workDir) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                compilerCommand,
                "-interaction=nonstopmode",
                texFileName
        );
        processBuilder.directory(workDir.toAbsolutePath().toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder logOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logOutput.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("编译超时（超过 " + timeoutSeconds + " 秒）");
        }
        return new CompileExecutionResult(process.exitValue(), logOutput.toString());
    }

    private boolean hasMissingGraphicsError(String logContent) {
        if (logContent == null || logContent.isEmpty()) {
            return false;
        }
        String normalized = logContent.toLowerCase(Locale.ROOT);
        return normalized.contains("unable to load picture or pdf file")
                || normalized.contains("file") && normalized.contains("not found")
                || normalized.contains("cannot determine size of graphic");
    }

    /**
     * 将 includegraphics 指令替换成可编译占位内容，避免图片文件缺失导致编译中断
     */
    private String buildMissingImageTolerantContent(String latexContent) {
        if (latexContent == null || latexContent.isEmpty()) {
            return latexContent;
        }
        String replaced = latexContent.replaceAll(
                "\\\\includegraphics\\s*(\\[[^\\]]*\\])?\\s*\\{([^}]*)\\}",
                "\\\\fbox{[missing image: $2]}"
        );
        // 保留 draft 选项作为额外兜底
        return "\\PassOptionsToPackage{draft}{graphicx}\n" + replaced;
    }

    private static class CompileExecutionResult {
        private final int exitCode;
        private final String logContent;

        private CompileExecutionResult(int exitCode, String logContent) {
            this.exitCode = exitCode;
            this.logContent = logContent;
        }
    }

    /**
     * 将 XeLaTeX 产出的 xdv 转换为 PDF
     */
    private String convertXdvToPdf(Path tempWorkDir, Path xdvFile) {
        StringBuilder logOutput = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "xdvipdfmx",
                    xdvFile.getFileName().toString()
            );
            pb.directory(tempWorkDir.toAbsolutePath().toFile());
            pb.redirectErrorStream(true);

            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logOutput.append(line).append("\n");
                }
            }
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                logOutput.append("xdvipdfmx 执行超时");
            }
        } catch (Exception e) {
            logOutput.append("xdvipdfmx 转换失败: ").append(e.getMessage());
        }
        return logOutput.toString().trim();
    }

    /**
     * 校验文件是否为有效PDF（最小校验：文件头 + 基本大小）
     */
    private boolean isValidPdf(Path pdfPath) {
        try {
            if (!Files.exists(pdfPath)) {
                return false;
            }
            long size = Files.size(pdfPath);
            if (size < 100) {
                return false;
            }
            try (InputStream in = Files.newInputStream(pdfPath)) {
                byte[] header = new byte[1024];
                int read = in.read(header);
                if (read <= 0) {
                    return false;
                }
                // PDF 规范允许 header 不一定出现在第 0 字节，通常在前 1KB 内
                for (int i = 0; i <= read - 5; i++) {
                    if (header[i] == '%' && header[i + 1] == 'P' && header[i + 2] == 'D' && header[i + 3] == 'F' && header[i + 4] == '-') {
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取编译器命令
     */
    private String getCompilerCommand(String compiler) {
        String normalizedCompiler = normalizeCompiler(compiler);
        switch (normalizedCompiler) {
            case "pdflatex":
                return pdflatexPath;
            case "xelatex":
                return resolveCompilerPath("xelatex", xelatexPath);
            case "lualatex":
                return resolveCompilerPath("lualatex", lualatexPath);
            default:
                return pdflatexPath;
        }
    }

    /**
     * 解析编译器命令路径：
     * 1) 优先使用显式配置的路径；
     * 2) 若为默认命令名，且 pdflatex-path 为绝对路径，则推导同目录下的引擎可执行文件。
     */
    private String resolveCompilerPath(String compilerName, String configuredPath) {
        if (configuredPath != null && !configuredPath.trim().isEmpty() && !compilerName.equalsIgnoreCase(configuredPath.trim())) {
            return configuredPath.trim();
        }

        if (pdflatexPath != null) {
            String pdflatex = pdflatexPath.trim();
            // 当 pdflatex-path 配的是绝对路径时，推导兄弟可执行文件（Windows/Linux/macOS 均兼容）
            if (pdflatex.contains("/") || pdflatex.contains("\\")) {
                Path pdflatexFile = Paths.get(pdflatex);
                Path parent = pdflatexFile.getParent();
                String fileName = pdflatexFile.getFileName() != null ? pdflatexFile.getFileName().toString() : "";
                if (parent != null && !fileName.isEmpty()) {
                    String suffix = fileName.toLowerCase(Locale.ROOT).endsWith(".exe") ? ".exe" : "";
                    Path siblingCompiler = parent.resolve(compilerName + suffix);
                    if (Files.exists(siblingCompiler)) {
                        return siblingCompiler.toString();
                    }
                }
            }
        }

        return compilerName;
    }

    /**
     * 规范化编译器参数，仅允许三种受支持引擎，非法值回退到 pdflatex
     */
    private String normalizeCompiler(String compiler) {
        if (compiler == null) {
            return "pdflatex";
        }
        String normalized = compiler.trim().toLowerCase();
        if ("xelatex".equals(normalized) || "lualatex".equals(normalized) || "pdflatex".equals(normalized)) {
            return normalized;
        }
        return "pdflatex";
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

