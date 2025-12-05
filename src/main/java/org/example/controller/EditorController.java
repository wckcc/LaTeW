package org.example.controller;

import org.example.dto.*;
import org.example.service.DocumentService;
import org.example.service.LaTeXService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 在线编辑器控制器
 * 提供在线排版、编译、导入导出等功能
 */
@RestController
@RequestMapping("/api/editor")
@CrossOrigin(origins = "*")
public class EditorController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private LaTeXService latexService;

    /**
     * 编译LaTeX文档为PDF
     * POST /api/editor/compile
     */
    @PostMapping("/compile")
    public ResponseResult<CompileResult> compileDocument(@RequestBody CompileRequest request) {
        // 验证文档是否存在
        DocumentDTO document = documentService.getDocumentById(request.getDocumentId());
        if (document == null) {
            return ResponseResult.error(404, "文档不存在");
        }

        // 执行编译
        CompileResult result = latexService.compileToPdf(request);
        return ResponseResult.success("编译完成", result);
    }

    /**
     * 验证LaTeX语法
     * POST /api/editor/validate
     */
    @PostMapping("/validate")
    public ResponseResult<CompileResult> validateLaTeX(@RequestBody String latexContent) {
        CompileResult result = latexService.validateLaTeX(latexContent);
        return ResponseResult.success(result);
    }

    /**
     * 导出为LaTeX格式
     * GET /api/editor/{documentId}/export/latex
     */
    @GetMapping("/{documentId}/export/latex")
    public ResponseEntity<String> exportToLaTeX(@PathVariable Long documentId) {
        DocumentDTO document = documentService.getDocumentById(documentId);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        String latexContent = latexService.exportToLaTeX(documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", 
            document.getTitle() + ".tex");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(latexContent);
    }

    /**
     * 导出为Word格式
     * GET /api/editor/{documentId}/export/word
     */
    @GetMapping("/{documentId}/export/word")
    public ResponseEntity<Resource> exportToWord(@PathVariable Long documentId) {
        DocumentDTO document = documentService.getDocumentById(documentId);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        File wordFile = latexService.exportToWord(documentId);
        if (wordFile == null || !wordFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(wordFile);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", 
            document.getTitle() + ".docx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * 导出为PDF格式
     * GET /api/editor/{documentId}/export/pdf
     */
    @GetMapping("/{documentId}/export/pdf")
    public ResponseEntity<Resource> exportToPdf(@PathVariable Long documentId) {
        DocumentDTO document = documentService.getDocumentById(documentId);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        File pdfFile = latexService.exportToPdf(documentId);
        if (pdfFile == null || !pdfFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(pdfFile);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", 
            document.getTitle() + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * 导入LaTeX文件
     * POST /api/editor/import/latex
     */
    @PostMapping("/import/latex")
    public ResponseResult<DocumentDTO> importLaTeXFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "useAIOptimize", defaultValue = "false") Boolean useAIOptimize) {
        
        if (file.isEmpty()) {
            return ResponseResult.error(400, "文件为空");
        }

        try {
            String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            String fileName = file.getOriginalFilename();
            
            ImportRequest request = new ImportRequest();
            request.setUserId(userId);
            request.setFileName(fileName);
            request.setFileContent(fileContent);
            request.setUseAIOptimize(useAIOptimize);

            DocumentDTO document = latexService.importFromLaTeX(request);
            return ResponseResult.success("文件导入成功", document);
        } catch (Exception e) {
            return ResponseResult.error(500, "文件导入失败: " + e.getMessage());
        }
    }

    /**
     * 获取编译日志
     * GET /api/editor/{documentId}/compile-log
     */
    @GetMapping("/{documentId}/compile-log")
    public ResponseResult<CompileResult> getCompileLog(@PathVariable Long documentId) {
        DocumentDTO document = documentService.getDocumentById(documentId);
        if (document == null) {
            return ResponseResult.error(404, "文档不存在");
        }

        CompileResult log = latexService.getCompileLog(documentId);
        return ResponseResult.success(log);
    }
}

