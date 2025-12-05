package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.dto.DocumentDTO;
import java.util.List;

/**
 * 文档Mapper接口
 */
@Mapper
public interface DocumentMapper {
    /**
     * 插入文档
     */
    int insert(DocumentDTO documentDTO);

    /**
     * 根据ID更新文档
     */
    int updateById(DocumentDTO documentDTO);

    /**
     * 根据ID删除文档
     */
    int deleteById(Long id);

    /**
     * 根据ID查询文档
     */
    DocumentDTO selectById(Long id);

    /**
     * 根据用户ID查询文档列表
     */
    List<DocumentDTO> selectByUserId(Long userId);

    /**
     * 查询所有文档
     */
    List<DocumentDTO> selectAll();

    /**
     * 更新文档内容
     */
    int updateContent(Long id, String content, String htmlContent);
}

