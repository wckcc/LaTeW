package org.example.service.impl;

import org.example.dto.*;
import org.example.mapper.CompileLogMapper;
import org.example.mapper.DocumentMapper;
import org.example.service.DocumentService;
import org.example.service.LaTeXService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LaTeX编译和导出服务实现类
 */
@Service
@Transactional
public class LaTeXServiceImpl implements LaTeXService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private CompileLogMapper compileLogMapper;

    @Autowired
    private DocumentService documentService;

    @Value("${latex.work.dir:./latex-work}")
    private String latexWorkDir;

    @Value("${latex.compiler.path:pdflatex}")
    private String latexCompilerPath;

    @Override
    public CompileResult compileToPdf(CompileRequest request) {
        long startTime = System.currentTimeMillis();
        CompileResult result = new CompileResult();
        result.setDocumentId(request.getDocumentId());
        result.setCreatedAt(LocalDateTime.now());

        try {
            // 获取文档
            DocumentDTO document = documentService.getDocumentById(request.getDocumentId());
            if (document == null) {
                result.setStatus("ERROR");
                result.setErrorMessage("文档不存在");
                compileLogMapper.insert(result);
                return result;
            }

            // 创建临时工作目录
            String workDir = createWorkDirectory();
            String texFileName = document.getTitle() != null ? 
                sanitizeFileName(document.getTitle()) : "document";
            String texFilePath = Paths.get(workDir, texFileName + ".tex").toString();

            // 写入LaTeX文件
            Files.write(Paths.get(texFilePath), document.getContent().getBytes("UTF-8"));

            // 确定编译器
            String compiler = request.getCompiler() != null ? 
                request.getCompiler() : (document.getLatexCompiler() != null ? 
                    document.getLatexCompiler() : "pdflatex");

            // 执行编译
            ProcessBuilder processBuilder = new ProcessBuilder(
                compiler,
                "-interaction=nonstopmode",
                "-output-directory=" + workDir,
                texFilePath
            );
            processBuilder.directory(new File(workDir));
            Process process = processBuilder.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 读取错误输出
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            long compileTime = System.currentTimeMillis() - startTime;

            result.setCompileTimeMs(compileTime);
            result.setLogContent(output.toString() + errorOutput.toString());
            result.setCompilerVersion(compiler);

            // 检查编译结果
            String pdfPath = Paths.get(workDir, texFileName + ".pdf").toString();
            File pdfFile = new File(pdfPath);

            if (exitCode == 0 && pdfFile.exists()) {
                result.setStatus("SUCCESS");
                result.setPdfPath(pdfPath);
                
                // 更新文档的最后编译时间
                document.setLastCompiledAt(LocalDateTime.now());
                documentMapper.updateById(document);
            } else {
                result.setStatus("ERROR");
                result.setErrorMessage("编译失败: " + errorOutput.toString());
            }

        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setErrorMessage("编译异常: " + e.getMessage());
            result.setLogContent(e.toString());
        } finally {
            // 保存编译日志
            compileLogMapper.insert(result);
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public String exportToLaTeX(Long documentId) {
        DocumentDTO document = documentService.getDocumentById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }
        return document.getContent() != null ? document.getContent() : "";
    }

    @Override
    @Transactional(readOnly = true)
    public File exportToWord(Long documentId) {
        // TODO: 实现LaTeX转Word功能
        // 可以使用pandoc或其他工具进行转换
        throw new UnsupportedOperationException("Word导出功能尚未实现");
    }

    @Override
    @Transactional(readOnly = true)
    public File exportToPdf(Long documentId) {
        // 先编译文档
        CompileRequest request = new CompileRequest();
        request.setDocumentId(documentId);
        
        CompileResult result = compileToPdf(request);
        
        if ("SUCCESS".equals(result.getStatus()) && result.getPdfPath() != null) {
            File pdfFile = new File(result.getPdfPath());
            if (pdfFile.exists()) {
                return pdfFile;
            }
        }
        
        throw new RuntimeException("PDF导出失败: " + result.getErrorMessage());
    }

    @Override
    public DocumentDTO importFromLaTeX(ImportRequest request) {
        try {
            // 创建文档DTO
            DocumentDTO document = new DocumentDTO();
            document.setUserId(request.getUserId());
            document.setTitle(extractTitleFromFileName(request.getFileName()));
            document.setContent(request.getFileContent());
            document.setStatus("DRAFT");
            document.setLatexCompiler("pdflatex");

            // 如果启用AI优化
            if (request.getUseAIOptimize() != null && request.getUseAIOptimize()) {
                // TODO: 调用AI服务优化LaTeX内容
                // 这里暂时跳过
            }

            // 保存文档
            return documentService.createDocument(document);
        } catch (Exception e) {
            throw new RuntimeException("导入LaTeX文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompileResult validateLaTeX(String latexContent) {
        CompileResult result = new CompileResult();
        result.setCreatedAt(LocalDateTime.now());
        result.setStatus("SUCCESS");

        try {
            // 创建临时文件
            String workDir = createWorkDirectory();
            String texFilePath = Paths.get(workDir, "validate.tex").toString();
            Files.write(Paths.get(texFilePath), latexContent.getBytes("UTF-8"));

            // 执行语法检查（使用latex -interaction=nonstopmode -halt-on-error）
            ProcessBuilder processBuilder = new ProcessBuilder(
                "pdflatex",
                "-interaction=nonstopmode",
                "-halt-on-error",
                "-output-directory=" + workDir,
                texFilePath
            );
            processBuilder.directory(new File(workDir));
            Process process = processBuilder.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            result.setLogContent(output.toString());

            if (exitCode != 0) {
                result.setStatus("ERROR");
                result.setErrorMessage("LaTeX语法验证失败");
            }

        } catch (Exception e) {
            result.setStatus("ERROR");
            result.setErrorMessage("验证异常: " + e.getMessage());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CompileResult getCompileLog(Long documentId) {
        return compileLogMapper.selectLatestByDocumentId(documentId);
    }

    /**
     * 创建工作目录
     */
    private String createWorkDirectory() throws IOException {
        String dir = Paths.get(latexWorkDir, UUID.randomUUID().toString()).toString();
        Files.createDirectories(Paths.get(dir));
        return dir;
    }

    /**
     * 清理文件名，移除非法字符
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "document";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * 从文件名提取标题
     */
    private String extractTitleFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "未命名文档";
        }
        // 移除扩展名
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
}

