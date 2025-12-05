package org.example.service.impl;

import org.example.dto.DocumentDTO;
import org.example.mapper.DocumentMapper;
import org.example.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档服务实现类
 */
@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {

    @Autowired
    private DocumentMapper documentMapper;

    @Override
    public DocumentDTO createDocument(DocumentDTO documentDTO) {
        // 设置默认值
        if (documentDTO.getStatus() == null || documentDTO.getStatus().isEmpty()) {
            documentDTO.setStatus("DRAFT");
        }
        
        if (documentDTO.getLatexCompiler() == null || documentDTO.getLatexCompiler().isEmpty()) {
            documentDTO.setLatexCompiler("pdflatex");
        }

        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        documentDTO.setCreatedAt(now);
        documentDTO.setUpdatedAt(now);

        // 插入文档
        int result = documentMapper.insert(documentDTO);
        if (result > 0) {
            return documentDTO;
        }
        throw new RuntimeException("创建文档失败");
    }

    @Override
    public DocumentDTO updateDocument(Long id, DocumentDTO documentDTO) {
        // 检查文档是否存在
        DocumentDTO existing = documentMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("文档不存在");
        }

        // 设置ID和更新时间
        documentDTO.setId(id);
        documentDTO.setUpdatedAt(LocalDateTime.now());

        // 更新文档
        int result = documentMapper.updateById(documentDTO);
        if (result > 0) {
            return documentMapper.selectById(id);
        }
        throw new RuntimeException("更新文档失败");
    }

    @Override
    public void deleteDocument(Long id) {
        // 检查文档是否存在
        DocumentDTO existing = documentMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("文档不存在");
        }

        // 删除文档
        int result = documentMapper.deleteById(id);
        if (result <= 0) {
            throw new RuntimeException("删除文档失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDTO getDocumentById(Long id) {
        return documentMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDTO> getDocumentsByUserId(Long userId) {
        return documentMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDTO> getAllDocuments() {
        return documentMapper.selectAll();
    }

    @Override
    public void saveDocumentContent(Long id, String content, String htmlContent) {
        // 检查文档是否存在
        DocumentDTO existing = documentMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("文档不存在");
        }

        // 更新文档内容
        int result = documentMapper.updateContent(id, content, htmlContent);
        if (result <= 0) {
            throw new RuntimeException("保存文档内容失败");
        }
    }
}

