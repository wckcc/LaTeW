package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.dto.ProjectDTO;

import java.util.List;

/**
 * 项目Mapper接口
 */
@Mapper
public interface ProjectMapper {
    /**
     * 插入项目
     * @param projectDTO 项目数据
     * @return 影响行数
     */
    int insert(ProjectDTO projectDTO);

    /**
     * 根据用户ID查询项目列表
     * @param userId 用户ID
     * @return 项目列表
     */
    List<ProjectDTO> selectByUserId(Long userId);
}

