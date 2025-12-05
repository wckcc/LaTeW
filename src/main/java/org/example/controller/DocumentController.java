package org.example.controller;

import org.example.dto.DocumentDTO;
import org.example.dto.ResponseResult;
import org.example.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档管理控制器
 * 提供文档的增删改查功能
 */
@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    /**
     * 创建文档
     * POST /api/documents
     */
    @PostMapping
    public ResponseResult<DocumentDTO> createDocument(@RequestBody DocumentDTO documentDTO) {
        DocumentDTO created = documentService.createDocument(documentDTO);
        return ResponseResult.success("文档创建成功", created);
    }

    /**
     * 根据ID获取文档
     * GET /api/documents/{id}
     */
    @GetMapping("/{id}")
    public ResponseResult<DocumentDTO> getDocumentById(@PathVariable Long id) {
        DocumentDTO document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseResult.error(404, "文档不存在");
        }
        return ResponseResult.success(document);
    }

    /**
     * 根据用户ID获取文档列表
     * GET /api/documents/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseResult<List<DocumentDTO>> getDocumentsByUser(@PathVariable Long userId) {
        List<DocumentDTO> documents = documentService.getDocumentsByUserId(userId);
        return ResponseResult.success(documents);
    }

    /**
     * 获取所有文档
     * GET /api/documents
     */
    @GetMapping
    public ResponseResult<List<DocumentDTO>> getAllDocuments() {
        List<DocumentDTO> documents = documentService.getAllDocuments();
        return ResponseResult.success(documents);
    }

    /**
     * 更新文档
     * PUT /api/documents/{id}
     */
    @PutMapping("/{id}")
    public ResponseResult<DocumentDTO> updateDocument(@PathVariable Long id, @RequestBody DocumentDTO documentDTO) {
        DocumentDTO existing = documentService.getDocumentById(id);
        if (existing == null) {
            return ResponseResult.error(404, "文档不存在");
        }
        documentDTO.setId(id);
        DocumentDTO updated = documentService.updateDocument(id, documentDTO);
        return ResponseResult.success("文档更新成功", updated);
    }

    /**
     * 保存文档内容（LaTeX和HTML）
     * PUT /api/documents/{id}/content
     */
    @PutMapping("/{id}/content")
    public ResponseResult<Void> saveDocumentContent(
            @PathVariable Long id,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String htmlContent) {
        DocumentDTO existing = documentService.getDocumentById(id);
        if (existing == null) {
            return ResponseResult.error(404, "文档不存在");
        }
        documentService.saveDocumentContent(id, content, htmlContent);
        return ResponseResult.success("文档内容保存成功", null);
    }

    /**
     * 删除文档
     * DELETE /api/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseResult<Void> deleteDocument(@PathVariable Long id) {
        DocumentDTO existing = documentService.getDocumentById(id);
        if (existing == null) {
            return ResponseResult.error(404, "文档不存在");
        }
        documentService.deleteDocument(id);
        return ResponseResult.success("文档删除成功", null);
    }
}

