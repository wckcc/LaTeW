package org.example.service.impl;

import org.example.dto.TemplateDTO;
import org.example.mapper.TemplateMapper;
import org.example.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模板服务实现类
 */
@Service
@Transactional
public class TemplateServiceImpl implements TemplateService {

    @Autowired
    private TemplateMapper templateMapper;

    @Override
    public TemplateDTO createTemplate(TemplateDTO templateDTO) {
        // 设置默认值
        if (templateDTO.getIsSystem() == null) {
            templateDTO.setIsSystem(false);
        }
        
        if (templateDTO.getUsageCount() == null) {
            templateDTO.setUsageCount(0);
        }

        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        templateDTO.setCreatedAt(now);
        templateDTO.setUpdatedAt(now);

        // 插入模板
        int result = templateMapper.insert(templateDTO);
        if (result > 0) {
            return templateDTO;
        }
        throw new RuntimeException("创建模板失败");
    }

    @Override
    public TemplateDTO updateTemplate(Long id, TemplateDTO templateDTO) {
        // 检查模板是否存在
        TemplateDTO existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("模板不存在");
        }

        // 系统模板不允许修改某些字段
        if (existing.getIsSystem() != null && existing.getIsSystem()) {
            // 系统模板不允许修改内容
            templateDTO.setContent(existing.getContent());
        }

        // 设置ID和更新时间
        templateDTO.setId(id);
        templateDTO.setUpdatedAt(LocalDateTime.now());

        // 更新模板
        int result = templateMapper.updateById(templateDTO);
        if (result > 0) {
            return templateMapper.selectById(id);
        }
        throw new RuntimeException("更新模板失败");
    }

    @Override
    public void deleteTemplate(Long id) {
        // 检查模板是否存在
        TemplateDTO existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("模板不存在");
        }

        // 系统模板不允许删除
        if (existing.getIsSystem() != null && existing.getIsSystem()) {
            throw new RuntimeException("系统模板不允许删除");
        }

        // 删除模板
        int result = templateMapper.deleteById(id);
        if (result <= 0) {
            throw new RuntimeException("删除模板失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateDTO getTemplateById(Long id) {
        return templateMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateDTO> getAllTemplates() {
        return templateMapper.selectAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateDTO> getTemplatesByCategory(String category) {
        return templateMapper.selectByCategory(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateDTO> getSystemTemplates() {
        return templateMapper.selectSystemTemplates();
    }

    @Override
    public void incrementUsageCount(Long templateId) {
        // 检查模板是否存在
        TemplateDTO existing = templateMapper.selectById(templateId);
        if (existing == null) {
            throw new RuntimeException("模板不存在");
        }

        // 增加使用次数
        int result = templateMapper.incrementUsageCount(templateId);
        if (result <= 0) {
            throw new RuntimeException("更新模板使用次数失败");
        }
    }
}

