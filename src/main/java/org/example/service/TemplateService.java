package org.example.service;

import org.example.dto.TemplateDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * 模板服务接口
 */
public interface TemplateService {
    /**
     * 创建模板
     */
    TemplateDTO createTemplate(TemplateDTO templateDTO);

    /**
     * 更新模板
     */
    TemplateDTO updateTemplate(Long id, TemplateDTO templateDTO);

    /**
     * 删除模板
     */
    void deleteTemplate(Long id);

    /**
     * 根据ID获取模板
     */
    TemplateDTO getTemplateById(Long id);

    /**
     * 获取所有模板
     */
    List<TemplateDTO> getAllTemplates();

    /**
     * 根据分类获取模板
     */
    List<TemplateDTO> getTemplatesByCategory(String category);

    /**
     * 获取系统模板
     */
    List<TemplateDTO> getSystemTemplates();

    /**
     * 增加模板使用次数
     */
    void incrementUsageCount(Long templateId);

    /**
     * 从 zip 包导入模板
     */
    int importTemplatesFromZip(MultipartFile zipFile, String templateName, String templateDescription);
}

