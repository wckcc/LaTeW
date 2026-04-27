package org.example.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.example.dto.AILogDTO;

/**
 * AI日志Mapper
 */
@Mapper
public interface AILogMapper {
    @Insert("insert into ai_log (project_id, request_type, input_content, output_content, created_at) " +
            "values (#{projectId}, #{requestType}, #{inputContent}, #{outputContent}, #{createdAt})")
    int insert(AILogDTO log);
}

