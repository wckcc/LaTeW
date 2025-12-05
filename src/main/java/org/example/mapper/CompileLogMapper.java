package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.dto.CompileResult;
import java.util.List;

/**
 * 编译日志Mapper接口
 */
@Mapper
public interface CompileLogMapper {
    /**
     * 插入编译日志
     */
    int insert(CompileResult compileResult);

    /**
     * 根据ID查询编译日志
     */
    CompileResult selectById(Long id);

    /**
     * 根据文档ID查询编译日志列表
     */
    List<CompileResult> selectByDocumentId(Long documentId);

    /**
     * 查询文档的最新编译日志
     */
    CompileResult selectLatestByDocumentId(Long documentId);
}

