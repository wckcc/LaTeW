package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.dto.TemplateDTO;
import java.util.List;

/**
 * 模板Mapper接口
 */
@Mapper
public interface TemplateMapper {
    /**
     * 插入模板
     */
    int insert(TemplateDTO templateDTO);

    /**
     * 根据ID更新模板
     */
    int updateById(TemplateDTO templateDTO);

    /**
     * 根据ID删除模板
     */
    int deleteById(Long id);

    /**
     * 根据ID查询模板
     */
    TemplateDTO selectById(Long id);

    /**
     * 查询所有模板
     */
    List<TemplateDTO> selectAll();

    /**
     * 根据分类查询模板
     */
    List<TemplateDTO> selectByCategory(String category);

    /**
     * 查询系统模板
     */
    List<TemplateDTO> selectSystemTemplates();

    /**
     * 增加模板使用次数
     */
    int incrementUsageCount(Long templateId);

    /**
     * 全部模板的 template_path（维护任务：孤儿模板 bundle 清理）。
     */
    List<String> selectAllTemplatePaths();
}

