package org.example.service;

import org.example.dto.DocumentDTO;
import java.util.List;

/**
 * 文档服务接口
 */
public interface DocumentService {
    /**
     * 创建文档
     */
    DocumentDTO createDocument(DocumentDTO documentDTO);

    /**
     * 更新文档
     */
    DocumentDTO updateDocument(Long id, DocumentDTO documentDTO);

    /**
     * 删除文档
     */
    void deleteDocument(Long id);

    /**
     * 根据ID获取文档
     */
    DocumentDTO getDocumentById(Long id);

    /**
     * 根据用户ID获取文档列表
     */
    List<DocumentDTO> getDocumentsByUserId(Long userId);

    /**
     * 获取所有文档
     */
    List<DocumentDTO> getAllDocuments();

    /**
     * 保存文档内容
     */
    void saveDocumentContent(Long id, String content, String htmlContent);
}

